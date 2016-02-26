/**
 * Copyright 2016 Myrle Krantz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mifos.module.stellar.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import javafx.util.Pair;
import org.mifos.module.stellar.federation.StellarAccountId;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.PathResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

import static org.mifos.module.stellar.service.StellarAccountHelpers.*;

@Component
public class HorizonServerUtilities {
  private static int STELLAR_MINIMUM_BALANCE = 20;
  private static int VAULT_ACCOUNT_INITIAL_BALANCE = STELLAR_MINIMUM_BALANCE + 1;
  //a transaction (for example to issue currency) costs 100 stroops. 10^-5 stellar.

  private final Logger logger;

  @Value("${stellar.horizon-address}")
  private String serverAddress;
  private Server server;

  @Value("${stellar.installation-account-private-key}")
  private String installationAccountPrivateKey;
  //TODO: keeping installationAccountPrivateKey as String? Should this be removed from memory?

  @Value("${stellar.new-account-initial-balance}")
  private int initialBalance = STELLAR_MINIMUM_BALANCE;

  @Value("${stellar.local-federation-domain}")
  private String localFederationDomain;

  private final LoadingCache<String, HorizonSequencer> accounts;

  @Autowired
  HorizonServerUtilities(@Qualifier("stellarBridgeLogger")final Logger logger)
  {
    this.logger = logger;

    int STELLAR_TRUSTLINE_BALANCE_REQUIREMENT = 10;
    int initialBalance = Math.max(this.initialBalance,
        STELLAR_MINIMUM_BALANCE + STELLAR_TRUSTLINE_BALANCE_REQUIREMENT);
    if (initialBalance != this.initialBalance) {
      logger.info("Initial balance cannot be lower than 30.  Configured value is being ignored: %i",
          this.initialBalance);
    }
    this.initialBalance = initialBalance;
    accounts = CacheBuilder.newBuilder().build(
        new CacheLoader<String, HorizonSequencer>() {
          public HorizonSequencer load(final String accountId)
              throws InvalidConfigurationException {
            final KeyPair accountKeyPair = KeyPair.fromAccountId(accountId);
            final StellarAccountHelpers account = getAccount(accountKeyPair);
            final Long sequenceNumber = account.get().getSequenceNumber() + 1;
            return new HorizonSequencer(accountKeyPair, sequenceNumber);
          }
        });
  }

  @PostConstruct
  void init()
  {
    server = new Server(serverAddress);
  }

  /**
   * Create an account on the stellar server to be used by a Mifos tenant.  This account will
   * need a minimum initial balance of 30 lumens, to be derived from the installation account.
   * A higher minimum can be configured, and should be if more than one trustline is needed.
   * A tenant-associated vault account uses up one trustline.
   *
   * @return The KeyPair of the account which was created.
   *
   * @throws InvalidConfigurationException if the horizon server named in the configuration cannot
   * be reached.  Either the address is wrong or the horizon server named is't running, or there is
   * a problem with the network.
   * @throws StellarAccountCreationFailedException if the horizon server refused the account
   * creation request.
   */
  public KeyPair createAccount()
      throws InvalidConfigurationException, StellarAccountCreationFailedException {
    logger.info("HorizonServerUtilities.createAccount");

    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);
    final HorizonSequencer installationAccount
        = accounts.getUnchecked(installationAccountKeyPair.getAccountId());

    final KeyPair newTenantStellarAccountKeyPair = KeyPair.random();

    createAccountForKeyPair(initialBalance, newTenantStellarAccountKeyPair,
        installationAccountKeyPair, installationAccount);

    setOptionsForNewAccount(newTenantStellarAccountKeyPair, installationAccountKeyPair);
    return newTenantStellarAccountKeyPair;
  }

  public KeyPair createVaultAccount()
    throws InvalidConfigurationException, StellarAccountCreationFailedException {
    logger.info("HorizonServerUtilities.createVaultAccount");

    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);
    final HorizonSequencer installationAccount
        = accounts.getUnchecked(installationAccountKeyPair.getAccountId());

    final KeyPair newTenantStellarVaultAccountKeyPair = KeyPair.random();

    createAccountForKeyPair(
        VAULT_ACCOUNT_INITIAL_BALANCE,
        newTenantStellarVaultAccountKeyPair,
        installationAccountKeyPair,
        installationAccount);

    return newTenantStellarVaultAccountKeyPair;
  }

  /**
   * Creates a line of trust between stellar accounts for one currency, and up to a maximum amount.
   *
   * @param stellarAccountPrivateKey the key of the account doing the trusting
   * @param issuingStellarAccountId the account Id of the account to be trusted.
   * @param assetCode the currency symbol of the currency to be trusted.  See
   *                 https://www.stellar.org/developers/learn/concepts/assets.html
   *                 for a description of how to create a valid asset code.
   * @param maximumAmount the maximum amount of the currency to be trusted.
   *
   * @throws InvalidConfigurationException if the horizon server named in the configuration cannot
   * be reached.  Either the address is wrong or the horizon server named is't running, or there is
   * a problem with the network.
   * @throws StellarTrustLineAdjustmentFailedException if the creation of the trustline failed for any
   * other reason.
   */
  public BigDecimal setTrustLineSize(
      final char[] stellarAccountPrivateKey,
      final StellarAccountId issuingStellarAccountId,
      final String assetCode,
      final BigDecimal maximumAmount)
      throws InvalidConfigurationException, StellarTrustLineAdjustmentFailedException
  {
    logger.info("HorizonServerUtilities.setTrustLineSize");
    final KeyPair trustingAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final HorizonSequencer trustingAccount
        = accounts.getUnchecked(trustingAccountKeyPair.getAccountId());

    final Asset asset = getAsset(assetCode, issuingStellarAccountId);

    final BigDecimal balance = getAccount(trustingAccountKeyPair).getBalanceOfAsset(asset);

    //Can't make it smaller than the balance
    final BigDecimal trustSize = balance.max(maximumAmount);

    final Transaction.Builder trustTransactionBuilder =
        new Transaction.Builder(trustingAccount.getAccount());

    final ChangeTrustOperation trustOperation =
        new ChangeTrustOperation.Builder(asset, bigDecimalToStellarBalance(trustSize)).build();

    trustTransactionBuilder.addOperation(trustOperation);

    submitTransaction(trustingAccount, trustTransactionBuilder,
        trustingAccountKeyPair, StellarTrustLineAdjustmentFailedException::trustLineTransactionFailed);

    return trustSize;
  }

  public void simplePay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final String assetCode,
      final StellarAccountId issuingAccountId,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    logger.info("HorizonServerUtilities.simplePay");
    final Asset asset = getAsset(assetCode, issuingAccountId);

    pay(targetAccountId, amount, asset, asset, stellarAccountPrivateKey);
  }

  private void pay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final Asset sendAsset,
      final Asset receiveAsset,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    final KeyPair sourceAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final KeyPair targetAccountKeyPair = KeyPair.fromAccountId(targetAccountId.getPublicKey());

    final HorizonSequencer sourceAccount = accounts.getUnchecked(sourceAccountKeyPair.getAccountId());

    final Transaction.Builder transferTransactionBuilder
        = new Transaction.Builder(sourceAccount.getAccount());
    final PathPaymentOperation paymentOperation =
        new PathPaymentOperation.Builder(
            sendAsset,
            bigDecimalToStellarBalance(amount),
            targetAccountKeyPair,
            receiveAsset,
            bigDecimalToStellarBalance(amount))
            .setSourceAccount(sourceAccountKeyPair).build();

    transferTransactionBuilder.addOperation(paymentOperation);

    if (targetAccountId.getSubAccount().isPresent())
    {
      final Memo subAccountMemo = Memo.text(targetAccountId.getSubAccount().get());
      transferTransactionBuilder.addMemo(subAccountMemo);
    }

    submitTransaction(sourceAccount, transferTransactionBuilder, sourceAccountKeyPair,
        StellarPaymentFailedException::transactionFailed);
  }

  public BigDecimal getBalance(
      final StellarAccountId stellarAccountId,
      final String assetCode)
  {
    logger.info("HorizonServerUtilities.getBalance");
    return getAccount(KeyPair.fromAccountId(stellarAccountId.getPublicKey())).getBalance(assetCode);
  }

  public BigDecimal getInstallationAccountBalance(
      final String assetCode,
      final StellarAccountId issuingStellarAccountId)
      throws InvalidConfigurationException
  {
    logger.info("HorizonServerUtilities.getInstallationAccountBalance");
    final StellarAccountId installationAccountId = StellarAccountId.mainAccount(
        KeyPair.fromSecretSeed(installationAccountPrivateKey).getAccountId());
    return this.getBalanceByIssuer(installationAccountId, assetCode, issuingStellarAccountId);
  }

  public BigDecimal getBalanceByIssuer(
      final StellarAccountId stellarAccountId,
      final String assetCode,
      final StellarAccountId accountIdOfIssuingStellarAddress)
      throws InvalidConfigurationException
  {
    logger.info("HorizonServerUtilities.getBalanceByIssuer");

    final Asset asset = getAsset(assetCode, accountIdOfIssuingStellarAddress);

    return getAccount(KeyPair.fromAccountId(stellarAccountId.getPublicKey()))
        .getBalanceOfAsset(asset);
  }

  public BigDecimal currencyTrustSize(
      final StellarAccountId trustingAccountId,
      final String assetCode,
      final StellarAccountId issuingAccountId)
  {
    logger.info("HorizonServerUtilities.currencyTrustSize");
    final StellarAccountHelpers trustingAccount
        = getAccount(KeyPair.fromAccountId(trustingAccountId.getPublicKey()));
    final Asset asset = getAsset(assetCode, issuingAccountId);

    return trustingAccount.getTrustInAsset(asset);
  }

  public void adjustOffer(
      final char[] stellarAccountPrivateKey,
      final StellarAccountId vaultAccountId,
      final Asset asset) {
    logger.info("HorizonServerUtilities.adjustOffer");
    if (!(asset instanceof AssetTypeCreditAlphaNum))
      return;

    final KeyPair accountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);

    final HorizonSequencer account = accounts.getUnchecked(accountKeyPair.getAccountId());

    final String assetCode = getAssetCode(asset);
    final Asset vaultAsset = getAsset(assetCode, vaultAccountId);

    final StellarAccountHelpers accountHelper = getAccount(accountKeyPair);
    final BigDecimal balanceOfVaultAsset = accountHelper.getBalanceOfAsset(vaultAsset);
    final BigDecimal remainingTrustInVaultAsset = accountHelper.getRemainingTrustInAsset(vaultAsset);

    final Transaction.Builder transactionBuilder = new Transaction.Builder(account.getAccount());
    accountHelper.getBalancesStream(assetCode, vaultAsset)
        .map(balance -> offerOperation(
                accountKeyPair,
                getAssetOfBalance(balance),
                vaultAsset,
                determineOfferAmount(balanceOfVaultAsset,
                    remainingTrustInVaultAsset,
                    stellarBalanceToBigDecimal(balance.getBalance()))))
        .forEach(transactionBuilder::addOperation);

    if (transactionBuilder.getOperationsCount() != 0)
      submitTransaction(account, transactionBuilder, accountKeyPair,
          StellarOfferAdjustmentFailedException::new);
  }

  static BigDecimal determineOfferAmount(
      final BigDecimal balanceOfVaultAsset,
      final BigDecimal remainingTrustInVaultAsset,
      final BigDecimal balanceOfMatchingAsset)
  {
    return remainingTrustInVaultAsset.min(balanceOfVaultAsset.min(balanceOfMatchingAsset));
  }


  private ManageOfferOperation offerOperation(
      final KeyPair sourceAccountKeyPair,
      final Asset fromAsset,
      final Asset toAsset,
      final BigDecimal amount)
  {
    final ManageOfferOperation.Builder offerOperationBuilder
        = new ManageOfferOperation.Builder(
        fromAsset, toAsset, bigDecimalToStellarBalance(amount), "1");
    offerOperationBuilder.setSourceAccount(sourceAccountKeyPair);
    return offerOperationBuilder.build();
  }

  private void createAccountForKeyPair(final int initialBalance, final KeyPair newAccountKeyPair,
      final KeyPair installationAccountKeyPair, final HorizonSequencer installationAccount)
      throws InvalidConfigurationException, StellarAccountCreationFailedException
  {
    final Transaction.Builder transactionBuilder
        = new Transaction.Builder(installationAccount.getAccount());

    final CreateAccountOperation createAccountOperation =
        new CreateAccountOperation.Builder(newAccountKeyPair,
            Integer.toString(initialBalance)).
            setSourceAccount(installationAccountKeyPair)
            .build();

    transactionBuilder.addOperation(createAccountOperation);

    submitTransaction(installationAccount, transactionBuilder, installationAccountKeyPair,
        StellarAccountCreationFailedException::new);
  }

  private void setOptionsForNewAccount(
      final KeyPair newAccountKeyPair,
      final KeyPair installationAccountKeyPair)
      throws StellarAccountCreationFailedException, InvalidConfigurationException
  {
    final HorizonSequencer newAccount = accounts.getUnchecked(newAccountKeyPair.getAccountId());
    final Transaction.Builder transactionBuilder = new Transaction.Builder(newAccount.getAccount());

    final SetOptionsOperation.Builder setOptionsOperationBuilder =
        new SetOptionsOperation.Builder().setSourceAccount(newAccountKeyPair);

    if (localFederationDomain != null)
    {
      setOptionsOperationBuilder.setHomeDomain(localFederationDomain);
    }

    setOptionsOperationBuilder.setInflationDestination(installationAccountKeyPair);

    transactionBuilder.addOperation(setOptionsOperationBuilder.build());

    submitTransaction(newAccount, transactionBuilder, newAccountKeyPair,
        StellarAccountCreationFailedException::new);
  }

  private StellarAccountHelpers getAccount(final KeyPair installationAccountKeyPair)
      throws InvalidConfigurationException
  {
    final AccountResponse installationAccount;
    try {
      installationAccount = server.accounts().account(installationAccountKeyPair);
    }
    catch (final IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }

    if (installationAccount == null)
    {
      throw InvalidConfigurationException.invalidInstallationAccountSecretSeed();
    }

    return new StellarAccountHelpers(installationAccount);
  }


  public void findPathPay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final String assetCode,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    logger.info("HorizonServerUtilities.findPathPay");
    final KeyPair sourceAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final KeyPair targetAccountKeyPair = KeyPair.fromAccountId(targetAccountId.getPublicKey());

    final StellarAccountHelpers sourceAccount = getAccount(sourceAccountKeyPair);
    final StellarAccountHelpers targetAccount = getAccount(targetAccountKeyPair);

    final Set<Asset> targetAssets = targetAccount.findAssetsWithTrust(amount, assetCode);
    final Set<Asset> sourceAssets = sourceAccount.findAssetsWithBalance(amount, assetCode);

    final Optional<Pair<Asset, Asset>> assetPair = findAnyMatchingAssetPair(
        amount, sourceAssets, targetAssets, sourceAccountKeyPair, targetAccountKeyPair);
    if (!assetPair.isPresent())
      throw StellarPaymentFailedException.noPathExists(assetCode);

    pay(targetAccountId, amount,
        assetPair.get().getKey(), assetPair.get().getValue(),
        stellarAccountPrivateKey);
  }

  private Optional<Pair<Asset, Asset>> findAnyMatchingAssetPair(
      final BigDecimal amount,
      final Set<Asset> sourceAssets,
      final Set<Asset> targetAssets,
      final KeyPair sourceAccountKeyPair,
      final KeyPair targetAccountKeyPair) {
    //TODO: retries.
    if (sourceAssets.isEmpty())
      return Optional.empty();

    for (final Asset targetAsset : targetAssets) {
      Page<PathResponse> paths;
      try {
        paths = server.paths()
            .sourceAccount(sourceAccountKeyPair)
            .destinationAccount(targetAccountKeyPair)
            .destinationAsset(targetAsset)
            .destinationAmount(bigDecimalToStellarBalance(amount))
            .execute();
      } catch (final IOException e) {
        return Optional.empty();
      }

      while (paths != null) {
        for (final PathResponse path : paths.getRecords())
        {
          if (stellarBalanceToBigDecimal(path.getSourceAmount()).compareTo(amount) <= 0)
          {
            if (sourceAssets.contains(path.getSourceAsset()))
            {
              return Optional.of(new Pair<>(path.getSourceAsset(), targetAsset));
            }
          }
        }

        try {
          paths = ((paths.getLinks() == null) || (paths.getLinks().getNext() == null)) ?
              null : paths.getNextPage();
        } catch (final URISyntaxException e) {
          throw new UnexpectedException();
        }
        catch (final IOException e) {
          return Optional.empty();
        }
      }
    }

    return Optional.empty();
  }

  private <T extends Exception> void submitTransaction(
      final HorizonSequencer transactionSubmitter,
      final Transaction.Builder transactionBuilder,
      final KeyPair signingKeyPair,
      final HorizonSequencer.TransactionFailedException<RuntimeException> failureHandler)
      throws T
  {
    try {
      transactionSubmitter.submitTransaction(server, transactionBuilder, signingKeyPair, logger,
          failureHandler);
    } catch (final IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
  }
}
