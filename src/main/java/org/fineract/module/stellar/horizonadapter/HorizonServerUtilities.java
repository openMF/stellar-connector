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
package org.fineract.module.stellar.horizonadapter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.AbstractMap;
import org.fineract.module.stellar.service.UnexpectedException;
import org.fineract.module.stellar.federation.StellarAccountId;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.stellar.sdk.Account;
import org.stellar.sdk.AccountMergeOperation;
import org.stellar.sdk.Asset;
import org.stellar.sdk.ChangeTrustOperation;
import org.stellar.sdk.CreateAccountOperation;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.ManageBuyOfferOperation;
import org.stellar.sdk.Memo;
import org.stellar.sdk.Server;
import org.stellar.sdk.SetOptionsOperation;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.PathResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

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

  @Value("${stellar.new-account-initial-balance}")
  private int initialBalance = STELLAR_MINIMUM_BALANCE;

  @Value("${stellar.local-federation-domain}")
  private String localFederationDomain;

  private final LoadingCache<String, Account> accounts;

  private final LoadingCache<AbstractMap.SimpleEntry<String, StellarAccountId>, Map<VaultOffer, Long>> offers;

  @Autowired
  HorizonServerUtilities(@Qualifier("stellarBridgeLogger") final Logger logger)
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
        new CacheLoader<String, Account>() {
          public Account load(final String accountId)
              throws InvalidConfigurationException {
            final KeyPair accountKeyPair = KeyPair.fromAccountId(accountId);
            final StellarAccountHelpers accountHelper = getAccount(accountKeyPair);
            final Long sequenceNumber = accountHelper.get().getSequenceNumber();
            return new Account(accountKeyPair.getAccountId(), sequenceNumber);
          }
        });

    offers = CacheBuilder.newBuilder().build(
        new CacheLoader<AbstractMap.SimpleEntry<String, StellarAccountId>, Map<VaultOffer, Long>>() {
          @Override
          public Map<VaultOffer, Long> load(final AbstractMap.SimpleEntry<String, StellarAccountId> accountIdVaultId) {
            return VaultOffer.getVaultOffers(
                server,
                accountIdVaultId.getKey(),
                accountIdVaultId.getValue());
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
    final Account installationAccount
        = accounts.getUnchecked(installationAccountKeyPair.getAccountId());

    final KeyPair newTenantStellarAccountKeyPair = KeyPair.random();

    createAccountForKeyPair(initialBalance, newTenantStellarAccountKeyPair,
        installationAccountKeyPair, installationAccount);

    setOptionsForNewAccount(newTenantStellarAccountKeyPair, installationAccountKeyPair);
    return newTenantStellarAccountKeyPair;
  }

  public void removeAccount(final StellarAccountId stellarAccountId,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, AccountMergerFailedException
  {
    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);

    StellarAccountHelpers account = getAccount(KeyPair.fromAccountId(stellarAccountId.getPublicKey()));

    if (account.getAllNonnativeBalancesStream().count() != 0)
      throw AccountMergerFailedException.accountIsNotEmpty(stellarAccountId.getPublicKey());

    mergeAccount(installationAccountKeyPair, KeyPair.fromSecretSeed(stellarAccountPrivateKey));
  }

  public KeyPair createVaultAccount()
    throws InvalidConfigurationException, StellarAccountCreationFailedException {
    logger.info("HorizonServerUtilities.createVaultAccount");

    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);
    final Account installationAccount
        = accounts.getUnchecked(installationAccountKeyPair.getAccountId());

    final KeyPair newTenantStellarVaultAccountKeyPair = KeyPair.random();

    createAccountForKeyPair(
        VAULT_ACCOUNT_INITIAL_BALANCE,
        newTenantStellarVaultAccountKeyPair,
        installationAccountKeyPair,
        installationAccount);

    setOptionsForNewVaultAccount(newTenantStellarVaultAccountKeyPair);

    return newTenantStellarVaultAccountKeyPair;
  }

  public void removeVaultAccount(
      final StellarAccountId stellarAccountId,
      final char[] stellarAccountPrivateKey,
      final StellarAccountId stellarVaultAccountId,
      final char[] stellarVaultAccountPrivateKey)
      throws InvalidConfigurationException,
      StellarPaymentFailedException,
      StellarTrustlineAdjustmentFailedException,
      AccountMergerFailedException
  {
    final KeyPair accountKeyPair = KeyPair.fromAccountId(stellarAccountId.getPublicKey());
    StellarAccountHelpers account = getAccount(accountKeyPair);

    account.getVaultBalancesStream(stellarVaultAccountId.getPublicKey()).forEach(
        balance -> adjustVaultIssuedAssets(stellarAccountId, stellarAccountPrivateKey,
            stellarVaultAccountId, stellarVaultAccountPrivateKey, balance.getAssetCode().orNull(),
            BigDecimal.ZERO));

    account = getAccount(accountKeyPair); //Get the new balances.

    if (0 != account.getVaultBalancesStream(stellarVaultAccountId.getPublicKey()).count())
      throw AccountMergerFailedException.vaultIssuedAssetsAreStillInCirculation();

    mergeAccount(accountKeyPair, KeyPair.fromSecretSeed(stellarVaultAccountPrivateKey));
  }

  private void mergeAccount(
      final KeyPair lastManStanding,
      final KeyPair dyingBreed)
      throws InvalidConfigurationException, AccountMergerFailedException
  {

    final Account accountSequencer = accounts.getUnchecked(dyingBreed.getAccountId());
    final AccountMergeOperation.Builder mergeOperation =
        new AccountMergeOperation.Builder(lastManStanding)
            .setSourceAccount(dyingBreed);

    final Transaction.Builder transactionBuilder = new Transaction.Builder(accountSequencer);
    transactionBuilder.addOperation(mergeOperation.build());

    submitTransaction(accountSequencer, transactionBuilder, dyingBreed, AccountMergerFailedException::stellarRefused);
  }

  public BigDecimal adjustVaultIssuedAssets(
      final StellarAccountId stellarAccountId,
      final char[] stellarAccountPrivateKey,
      final StellarAccountId stellarVaultAccountId,
      final char[] stellarVaultAccountPrivateKey,
      final String assetCode,
      final BigDecimal amount)
      throws InvalidConfigurationException,
      StellarPaymentFailedException,
      StellarTrustlineAdjustmentFailedException
  {
    final BigDecimal currentVaultIssuedAssets =
        currencyTrustSize(stellarAccountId, assetCode, stellarVaultAccountId);

    final BigDecimal adjustmentRequired = amount.subtract(currentVaultIssuedAssets);

    if (adjustmentRequired.compareTo(BigDecimal.ZERO) < 0)
    {
      final BigDecimal currentVaultIssuedAssetsHeldByTenant =
          getBalanceByIssuer(stellarAccountId, assetCode, stellarVaultAccountId);

      final BigDecimal adjustmentPossible
          = currentVaultIssuedAssetsHeldByTenant.min(adjustmentRequired.abs());

      final BigDecimal finalBalance = currentVaultIssuedAssets.subtract(adjustmentPossible);

      simplePay(
          stellarVaultAccountId,
          adjustmentPossible,
          assetCode,
          stellarVaultAccountId,
          stellarAccountPrivateKey);

      setTrustLineSize(
          stellarAccountPrivateKey, stellarVaultAccountId, assetCode,
          finalBalance);

      return finalBalance;
    }
    else if (adjustmentRequired.compareTo(BigDecimal.ZERO) > 0)
    {
      setTrustLineSize(
          stellarAccountPrivateKey, stellarVaultAccountId, assetCode,
          amount);

      simplePay(
          stellarAccountId,
          adjustmentRequired,
          assetCode,
          stellarVaultAccountId,
          stellarVaultAccountPrivateKey);

      return amount;
    }
    else {
      return currentVaultIssuedAssets;
    }
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
   * @throws StellarTrustlineAdjustmentFailedException if the creation of the trustline failed for any
   * other reason.
   */
  public BigDecimal setTrustLineSize(
      final char[] stellarAccountPrivateKey,
      final StellarAccountId issuingStellarAccountId,
      final String assetCode,
      final BigDecimal maximumAmount)
      throws InvalidConfigurationException, StellarTrustlineAdjustmentFailedException
  {
    logger.info("HorizonServerUtilities.setTrustLineSize");
    final KeyPair trustingAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final Account trustingAccount
        = accounts.getUnchecked(trustingAccountKeyPair.getAccountId());

    final Asset asset = StellarAccountHelpers.getAsset(assetCode, issuingStellarAccountId);

    final BigDecimal balance = getAccount(trustingAccountKeyPair).getBalanceOfAsset(asset);

    //Can't make it smaller than the balance
    final BigDecimal trustSize = balance.max(maximumAmount);

    final Transaction.Builder trustTransactionBuilder =
        new Transaction.Builder(trustingAccount);

    final ChangeTrustOperation trustOperation =
        new ChangeTrustOperation.Builder(asset, StellarAccountHelpers.bigDecimalToStellarBalance(trustSize)).build();

    trustTransactionBuilder.addOperation(trustOperation);

    submitTransaction(trustingAccount, trustTransactionBuilder,
        trustingAccountKeyPair, StellarTrustlineAdjustmentFailedException::trustLineTransactionFailed);

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
    final Asset asset = StellarAccountHelpers.getAsset(assetCode, issuingAccountId);

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

    final Account sourceAccount = accounts.getUnchecked(sourceAccountKeyPair.getAccountId());

    final Transaction.Builder transferTransactionBuilder
        = new Transaction.Builder(sourceAccount);
    final PathPaymentOperation paymentOperation =
        new PathPaymentOperation.Builder(
            sendAsset,
            StellarAccountHelpers.bigDecimalToStellarBalance(amount),
            targetAccountKeyPair,
            receiveAsset,
            StellarAccountHelpers.bigDecimalToStellarBalance(amount))
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

    final Asset asset = StellarAccountHelpers.getAsset(assetCode, accountIdOfIssuingStellarAddress);

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
    final Asset asset = StellarAccountHelpers.getAsset(assetCode, issuingAccountId);

    return trustingAccount.getTrustInAsset(asset);
  }

  public void adjustOffer(
      final char[] stellarAccountPrivateKey,
      final StellarAccountId vaultAccountId,
      final String assetCode)
      throws InvalidConfigurationException, StellarOfferAdjustmentFailedException {
    logger.info("HorizonServerUtilities.adjustOffer");

    final KeyPair accountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);

    final Account account = accounts.getUnchecked(accountKeyPair.getAccountId());

    final Asset vaultAsset = StellarAccountHelpers.getAsset(assetCode, vaultAccountId);

    final StellarAccountHelpers accountHelper = getAccount(accountKeyPair);
    final BigDecimal balanceOfVaultAsset = accountHelper.getBalanceOfAsset(vaultAsset);
    final BigDecimal remainingTrustInVaultAsset = accountHelper.getRemainingTrustInAsset(vaultAsset);

    final AbstractMap.SimpleEntry<String, StellarAccountId> offerKey =
        new AbstractMap.SimpleEntry<>(accountKeyPair.getAccountId(), vaultAccountId);
    offers.refresh(offerKey);
    final Map<VaultOffer, Long> vaultOffers = offers.getUnchecked(offerKey);

    final Transaction.Builder transactionBuilder = new Transaction.Builder(account);
    accountHelper.getAllNonnativeBalancesStream(assetCode, vaultAsset)
        .filter(balance -> !balance.getAssetIssuer().equals(vaultAccountId.getPublicKey()))
        .map(balance -> offerOperation(
                accountKeyPair,
                StellarAccountHelpers.getAssetOfBalance(balance),
                vaultAsset,
                determineOfferAmount(balanceOfVaultAsset,
                    remainingTrustInVaultAsset,
                    StellarAccountHelpers.stellarBalanceToBigDecimal(balance.getBalance())),
                determineOfferId(vaultOffers, balance)))
        .forEach(transactionBuilder::addOperation);

    if (transactionBuilder.getOperationsCount() != 0) {
      submitTransaction(account, transactionBuilder, accountKeyPair,
          StellarOfferAdjustmentFailedException::new);
    }
  }

  static BigDecimal determineOfferAmount(
      final BigDecimal balanceOfVaultAsset,
      final BigDecimal remainingTrustInVaultAsset,
      final BigDecimal balanceOfMatchingAsset)
  {
    return remainingTrustInVaultAsset.min(balanceOfVaultAsset.min(balanceOfMatchingAsset));
  }

  Optional<Long> determineOfferId(
      final Map<VaultOffer, Long> vaultOffers,
      final AccountResponse.Balance balance)
  {
    return Optional.ofNullable(
        vaultOffers.get(new VaultOffer(balance.getAssetCode().orNull(), balance.getAssetIssuer().orNull())));
  }

  private ManageBuyOfferOperation offerOperation(
      final KeyPair sourceAccountKeyPair,
      final Asset fromAsset,
      final Asset toAsset,
      final BigDecimal amount,
      final Optional<Long> offerId)
  {
    final ManageBuyOfferOperation.Builder offerOperationBuilder
        = new ManageBuyOfferOperation.Builder(
        fromAsset, toAsset, StellarAccountHelpers.bigDecimalToStellarBalance(amount), "1");
    offerOperationBuilder.setSourceAccount(sourceAccountKeyPair.getAccountId());

    offerId.ifPresent(offerOperationBuilder::setOfferId);

    return offerOperationBuilder.build();
  }

  private void createAccountForKeyPair(final int initialBalance, final KeyPair newAccountKeyPair,
      final KeyPair installationAccountKeyPair, final Account installationAccount)
      throws InvalidConfigurationException, StellarAccountCreationFailedException
  {
    final Transaction.Builder transactionBuilder
        = new Transaction.Builder(installationAccount);

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
    final Account newAccount = accounts.getUnchecked(newAccountKeyPair.getAccountId());
    final Transaction.Builder transactionBuilder = new Transaction.Builder(newAccount);

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

  private void setOptionsForNewVaultAccount(
      final KeyPair newAccountKeyPair)
      throws StellarAccountCreationFailedException, InvalidConfigurationException
  {
    final Account newAccount = accounts.getUnchecked(newAccountKeyPair.getAccountId());
    final Transaction.Builder transactionBuilder = new Transaction.Builder(newAccount);

    final SetOptionsOperation.Builder setOptionsOperationBuilder =
        new SetOptionsOperation.Builder().setSourceAccount(newAccountKeyPair);

    setOptionsOperationBuilder.setSetFlags(0x2);

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
    catch (final Exception e) {
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

    final Optional<AbstractMap.SimpleEntry<Asset, Asset>> assetPair = findAnyMatchingAssetPair(
        amount, sourceAssets, targetAssets, sourceAccountKeyPair, targetAccountKeyPair);
    if (!assetPair.isPresent())
      throw StellarPaymentFailedException.noPathExists(assetCode);

    pay(targetAccountId, amount,
        assetPair.get().getKey(), assetPair.get().getValue(),
        stellarAccountPrivateKey);
  }

  private Optional<AbstractMap.SimpleEntry<Asset, Asset>> findAnyMatchingAssetPair(
      final BigDecimal amount,
      final Set<Asset> sourceAssets,
      final Set<Asset> targetAssets,
      final KeyPair sourceAccountKeyPair,
      final KeyPair targetAccountKeyPair) {
    if (sourceAssets.isEmpty())
      return Optional.empty();

    for (final Asset targetAsset : targetAssets) {
      Page<PathResponse> paths;
      try {
        paths = server.paths()
            .sourceAccount(sourceAccountKeyPair)
            .destinationAccount(targetAccountKeyPair)
            .destinationAsset(targetAsset)
            .destinationAmount(StellarAccountHelpers.bigDecimalToStellarBalance(amount))
            .execute();
      } catch (final Exception e) {
        return Optional.empty();
      }

      while (paths != null && paths.getRecords() != null) {
        for (final PathResponse path : paths.getRecords())
        {
          if (StellarAccountHelpers.stellarBalanceToBigDecimal(path.getSourceAmount()).compareTo(amount) <= 0)
          {
            if (sourceAssets.contains(path.getSourceAsset()))
            {
              return Optional.of(new AbstractMap.SimpleEntry<>(path.getSourceAsset(), targetAsset));
            }
          }
        }

        try {
          paths = ((paths.getLinks() == null) || (paths.getLinks().getNext() == null)) ?
              null : paths.getNextPage();
        } catch (final Exception e) {
          throw new UnexpectedException();
        }
      }
    }

    return Optional.empty();
  }

  private <T extends Exception> void submitTransaction(
      final Account transactionSubmitter,
      final Transaction.Builder transactionBuilder,
      final KeyPair signingKeyPair,
      final Supplier<T> failureHandler)
      throws T
  {
    try {
      //final Long sequenceNumberSubmitted = account.getSequenceNumber();

      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (transactionSubmitter) {
        final Transaction transaction = transactionBuilder.build();
        transaction.sign(signingKeyPair);
        final SubmitTransactionResponse transactionResponse = server.submitTransaction(transaction);
        if (!transactionResponse.isSuccess()) {
          if (transactionResponse.getExtras() != null) {
            logger.info("Stellar transaction failed, request: {}", transactionResponse.getExtras().getEnvelopeXdr());
            logger.info("Stellar transaction failed, response: {}", transactionResponse.getExtras().getResultXdr());
          }
          else
          {
            logger.info("Stellar transaction failed.  No extra information available.");
          }
          //TODO: resend transaction if you get a bad sequence.
              /*Thread.sleep(6000); //Wait for ledger to close.
              Long sequenceNumberShouldHaveBeen =
                  server.accounts().account(account.getKeypair()).getSequenceNumber();
              if (sequenceNumberSubmitted != sequenceNumberShouldHaveBeen) {
                logger.info("Sequence number submitted: {}, Sequence number should have been: {}",
                    sequenceNumberSubmitted, sequenceNumberShouldHaveBeen);
              }*/
          throw failureHandler.get();
        }
      }
    } catch (final IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
  }
}
