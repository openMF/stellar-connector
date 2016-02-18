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
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.mifos.module.stellar.service.StellarAccountHelpers.*;

@Component
public class HorizonServerUtilities {
  static String getAssetCode(final Asset asset) {
    if (asset instanceof AssetTypeCreditAlphaNum)
    {
      return ((AssetTypeCreditAlphaNum)asset).getCode();
    }
    else
    {
      return "XLM";
    }
  }

  private final Logger logger;

  @Value("${stellar.horizon-address}")
  private String serverAddress;
  private Server server;

  @Value("${stellar.installation-account-private-key}")
  private String installationAccountPrivateKey;
  //TODO: keeping installationAccountPrivateKey as String? Should this be removed from memory?

  @Value("${stellar.new-account-initial-balance}")
  private int initialBalance = 20;

  @Value("${stellar.local-federation-domain}")
  private String localFederationDomain;

  @Autowired
  HorizonServerUtilities(@Qualifier("stellarBridgeLogger")final Logger logger)
  {
    this.logger = logger;
  }

  @PostConstruct
  void init()
  {
    server = new Server(serverAddress);
  }

  /**
   * Create an account on the stellar server to be used by a Mifos tenant.  This account will
   * need a minimum initial balance of 20 lumens, to be derived from the installation account.
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

    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);
    final AccountResponse installationAccount = getAccount(installationAccountKeyPair);

    final KeyPair newTenantStellarAccountKeyPair = KeyPair.random();

    createAccountForKeyPair(newTenantStellarAccountKeyPair, installationAccountKeyPair,
        installationAccount);

    setOptionsForNewAccount(newTenantStellarAccountKeyPair, installationAccountKeyPair);

    return newTenantStellarAccountKeyPair;
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
    final KeyPair trustingAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final AccountResponse trustingAccount = getAccount(trustingAccountKeyPair);

    final Asset asset = getAsset(assetCode, issuingStellarAccountId);

    final BigDecimal balance = getBalanceOfAsset(trustingAccount, asset);

    //Can't make it smaller than the balance
    final BigDecimal trustSize = balance.max(maximumAmount);

    final Transaction.Builder trustTransactionBuilder =
        new Transaction.Builder(trustingAccount);

    final ChangeTrustOperation trustOperation =
        new ChangeTrustOperation.Builder(asset, bigDecimalToStellarBalance(trustSize)).build();

    trustTransactionBuilder.addOperation(trustOperation);

    final Transaction trustTransaction = trustTransactionBuilder.build();

    trustTransaction.sign(trustingAccountKeyPair);

    submitTransaction(
        trustTransaction, StellarTrustLineAdjustmentFailedException::trustLineTransactionFailed);

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
    final Asset asset = getAsset(assetCode, issuingAccountId);

    pay(targetAccountId, amount, asset, asset, stellarAccountPrivateKey);
  }

  public void pay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final Asset sendAsset,
      final Asset receiveAsset,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    final KeyPair sourceAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final KeyPair targetAccountKeyPair = KeyPair.fromAccountId(targetAccountId.getPublicKey());

    final AccountResponse sourceAccount = getAccount(sourceAccountKeyPair);

    final Transaction.Builder transferTransactionBuilder = new Transaction.Builder(sourceAccount);
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

    final Transaction transferTransaction = transferTransactionBuilder.build();
    transferTransaction.sign(sourceAccountKeyPair);


    submitTransaction(transferTransaction, StellarPaymentFailedException::transactionFailed);
  }

  public BigDecimal getBalance(
      final StellarAccountId stellarAccountId,
      final String assetCode)
  {
    final KeyPair accountKeyPair = KeyPair.fromAccountId(stellarAccountId.getPublicKey());
    final AccountResponse tenantAccount = getAccount(accountKeyPair);
    return StellarAccountHelpers.getBalance(tenantAccount, assetCode);
  }

  public BigDecimal getInstallationAccountBalance(
      final String assetCode,
      final StellarAccountId issuingStellarAccountId)
      throws InvalidConfigurationException
  {
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
    final KeyPair accountKeyPair = KeyPair.fromAccountId(stellarAccountId.getPublicKey());

    final AccountResponse account = getAccount(accountKeyPair);

    final Asset asset = getAsset(assetCode, accountIdOfIssuingStellarAddress);

    return getBalanceOfAsset(account, asset);
  }

  public BigDecimal currencyTrustSize(
      final StellarAccountId trustingAccountId,
      final String assetCode,
      final StellarAccountId issuingAccountId)
  {
    final KeyPair trustingAccountKeyPair = KeyPair.fromAccountId(trustingAccountId.getPublicKey());

    final AccountResponse trustingAccount = getAccount(trustingAccountKeyPair);
    final Asset asset = getAsset(assetCode, issuingAccountId);

    return getNumericAspectOfAsset(trustingAccount, asset,
        balance -> stellarBalanceToBigDecimal(balance.getLimit()));
  }

  public void adjustOffer(
      final char[] stellarAccountPrivateKey,
      final StellarAccountId vaultAccountId,
      final Asset asset) {
    if (!(asset instanceof AssetTypeCreditAlphaNum))
      return;

    final KeyPair accountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);

    final AccountResponse account = getAccount(accountKeyPair);

    final String assetCode = getAssetCode(asset);
    final Asset vaultAsset = getAsset(assetCode, vaultAccountId);

    final BigDecimal balanceOfVaultAsset = getBalanceOfAsset(account, vaultAsset);

    final Transaction.Builder transactionBuilder = new Transaction.Builder(account);
    Arrays.asList(account.getBalances()).stream()
        .filter(balance -> balanceIsInAsset(balance, assetCode))
        .filter(balance -> !getAssetOfBalance(balance).equals(vaultAsset))
        .map(balance -> offerOperation(
                accountKeyPair,
                getAssetOfBalance(balance),
                vaultAsset,
                balanceOfVaultAsset.min(stellarBalanceToBigDecimal(balance.getBalance()))))
        .forEachOrdered(transactionBuilder::addOperation);

    final Transaction transaction = transactionBuilder.build();
    transaction.sign(accountKeyPair);

    submitTransaction(transaction, StellarOfferAdjustmentFailedException::new);
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



  private void createAccountForKeyPair(
      final KeyPair newAccountKeyPair,
      final KeyPair installationAccountKeyPair,
      final AccountResponse installationAccount)
      throws InvalidConfigurationException, StellarAccountCreationFailedException
  {
    final Transaction.Builder transactionBuilder = new Transaction.Builder(installationAccount);

    int initialBalance = Math.max(this.initialBalance, 20);
    if (initialBalance != this.initialBalance) {
      logger.info("Initial balance cannot be lower than 20.  Configured value is being ignored: %i",
          this.initialBalance);
    }

    final CreateAccountOperation createAccountOperation =
        new CreateAccountOperation.Builder(newAccountKeyPair,
            Integer.toString(initialBalance)).
            setSourceAccount(installationAccountKeyPair)
            .build();

    transactionBuilder.addOperation(createAccountOperation);

    final Transaction createAccountTransaction = transactionBuilder.build();

    createAccountTransaction.sign(installationAccountKeyPair);

    submitTransaction(createAccountTransaction, StellarAccountCreationFailedException::new);
  }

  private void setOptionsForNewAccount(
      final KeyPair newAccountKeyPair,
      final KeyPair installationAccountKeyPair)
      throws StellarAccountCreationFailedException, InvalidConfigurationException
  {
    final AccountResponse newAccount = getAccount(newAccountKeyPair);
    final Transaction.Builder transactionBuilder = new Transaction.Builder(newAccount);

    final SetOptionsOperation.Builder setOptionsOperationBuilder =
        new SetOptionsOperation.Builder().setSourceAccount(newAccountKeyPair);

    if (localFederationDomain != null)
    {
      setOptionsOperationBuilder.setHomeDomain(localFederationDomain);
    }

    setOptionsOperationBuilder.setInflationDestination(installationAccountKeyPair);

    transactionBuilder.addOperation(setOptionsOperationBuilder.build());

    final Transaction setOptionsTransaction = transactionBuilder.build();

    setOptionsTransaction.sign(newAccountKeyPair);
    submitTransaction(setOptionsTransaction, StellarAccountCreationFailedException::new);
  }

  private AccountResponse getAccount(final KeyPair installationAccountKeyPair)
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
    return installationAccount;
  }


  public void findPathPay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final String assetCode,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    final KeyPair sourceAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final KeyPair targetAccountKeyPair = KeyPair.fromAccountId(targetAccountId.getPublicKey());

    final AccountResponse sourceAccount = getAccount(sourceAccountKeyPair);
    final AccountResponse targetAccount = getAccount(targetAccountKeyPair);

    final Set<Asset>
        targetAssets = findAssetsWithTrust(targetAccount, amount, assetCode);
    final Set<Asset>
        sourceAssets = findAssetsWithBalance(sourceAccount, amount, assetCode);

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
            if (sourceAssets.stream().anyMatch(
                sourceAsset -> sourceAsset.equals(path.getSourceAsset())))
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

  private interface TransactionFailedException<T extends Exception> {
    T exceptionWhenTransactionFails();
  }

  private <T extends Exception> void submitTransaction(
      final Transaction transaction,
      final TransactionFailedException<T> failureHandler) throws T
  {
    try {
      final SubmitTransactionResponse transactionResponse =
          server.submitTransaction(transaction);
      if (!transactionResponse.isSuccess())
      {
        //TODO: resend transaction if you get a bad sequence.
        throw failureHandler.exceptionWhenTransactionFails();
      }
      //TODO: find a way to communicate back fees
    } catch (final IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
  }
}
