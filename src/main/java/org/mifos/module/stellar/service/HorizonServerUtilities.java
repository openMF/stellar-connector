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

import org.mifos.module.stellar.federation.StellarAccountId;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stellar.base.*;
import org.stellar.sdk.Account;
import org.stellar.sdk.Server;
import org.stellar.sdk.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

@Component
public class HorizonServerUtilities {

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
    final Account installationAccount = getAccount(server, installationAccountKeyPair);

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
    final Account trustingAccount = getAccount(server, trustingAccountKeyPair);

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
    pay(targetAccountId, amount, assetCode,
        issuingAccountId, issuingAccountId, stellarAccountPrivateKey);
  }

  public void pay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final String assetCode,
      final StellarAccountId sourceIssuer,
      final StellarAccountId targetIssuer,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    final KeyPair sourceAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final KeyPair targetAccountKeyPair = KeyPair.fromAccountId(targetAccountId.getPublicKey());

    final Asset sendAsset = getAsset(assetCode, sourceIssuer);
    final Asset receiveAsset = getAsset(assetCode, targetIssuer);

    final Account sourceAccount = getAccount(server, sourceAccountKeyPair);

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


    submitTransaction(transferTransaction, StellarPaymentFailedException::new);
  }

  private Asset getAsset(final String assetCode, final StellarAccountId targetIssuer) {
    return Asset.createNonNativeAsset(assetCode, KeyPair.fromAccountId(targetIssuer.getPublicKey()));
  }

  public BigDecimal getBalance(
      final StellarAccountId stellarAccountId,
      final String assetCode)
  {
    final KeyPair accountKeyPair = KeyPair.fromAccountId(stellarAccountId.getPublicKey());
    return getBalance(accountKeyPair, assetCode);
  }

  public BigDecimal getInstallationAccountBalance(
      final String assetCode,
      final StellarAccountId issuingStellarAccountId) {
    final StellarAccountId installationAccountId = StellarAccountId.mainAccount(
        KeyPair.fromSecretSeed(installationAccountPrivateKey).getAccountId());
    return this.getBalanceByIssuer(installationAccountId, assetCode, issuingStellarAccountId);
  }

  public BigDecimal getBalanceByIssuer(
      final StellarAccountId stellarAccountId,
      final String assetCode,
      final StellarAccountId accountIdOfIssuingStellarAddress)
  {
    final KeyPair accountKeyPair = KeyPair.fromAccountId(stellarAccountId.getPublicKey());

    final Account account = getAccount(server, accountKeyPair);

    final Asset asset = getAsset(assetCode, accountIdOfIssuingStellarAddress);

    return getBalanceOfAsset(account, asset);
  }

  private BigDecimal getBalance(final KeyPair accountKeyPair, final String assetCode) {
    final Account tenantAccount = getAccount(server, accountKeyPair);
    final Account.Balance[] balances = tenantAccount.getBalances();

    return Arrays.asList(balances).stream()
        .filter(balance -> balanceIsInAsset(balance, assetCode))
        .map(balance -> stellarBalanceToBigDecimal(balance.getBalance()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal currencyTrustSize(
      final StellarAccountId trustingAccountId,
      final String assetCode,
      final StellarAccountId issuingAccountId)
  {
    final KeyPair trustingAccountKeyPair = KeyPair.fromAccountId(trustingAccountId.getPublicKey());

    final Account trustingAccount = getAccount(server, trustingAccountKeyPair);
    final Asset asset = getAsset(assetCode, issuingAccountId);

    return getBalanceOfAsset(trustingAccount, asset);
  }

  private void createAccountForKeyPair(
      final KeyPair newAccountKeyPair,
      final KeyPair installationAccountKeyPair,
      final Account installationAccount)
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
    final Account newAccount = getAccount(server, newAccountKeyPair);
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

  private Account getAccount(final Server server, final KeyPair installationAccountKeyPair)
      throws InvalidConfigurationException
  {
    final Account installationAccount;
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


  private interface TransactionFailedException<T extends Exception> {
    T exceptionWhenTransactionFails();
  }

  private <T extends Exception> void submitTransaction(
      final Transaction transaction,
      final TransactionFailedException<T> failureHandler) throws T
  {
    try {
      final SubmitTransactionResponse createTrustLineResponse =
          server.submitTransaction(transaction);
      if (!createTrustLineResponse.isSuccess())
      {
        throw failureHandler.exceptionWhenTransactionFails();
      }
      //TODO: find a way to communicate back fees
    } catch (final IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
  }

  private BigDecimal getBalanceOfAsset(
      final Account sourceAccount,
      final Asset asset)
  {
    final Optional<BigDecimal> balanceOfGivenAsset
        = Arrays.asList(sourceAccount.getBalances()).stream()
        .filter(balance -> getAssetOfBalance(balance).equals(asset))
        .map(balance -> stellarBalanceToBigDecimal(balance.getBalance()))
        .max(BigDecimal::compareTo);

    //Theoretically there shouldn't be more than one balance, but if this should turn out to be
    //incorrect, we return the largest one, rather than adding them together.

    return balanceOfGivenAsset.orElse(BigDecimal.ZERO);
  }

  private boolean balanceIsInAsset(
      final Account.Balance balance, final String assetCode)
  {
    if (balance.getAssetType() == null)
      return false;

    if (balance.getAssetCode() == null) {
      return assetCode.equals("XLM") && balance.getAssetType().equals("native");
    }

    return balance.getAssetCode().equals(assetCode);
  }

  private Asset getAssetOfBalance(
      final Account.Balance balance)
  {
    if (balance.getAssetCode() == null)
      return new AssetTypeNative();
    else
      return Asset.createNonNativeAsset(balance.getAssetCode(),
        KeyPair.fromAccountId(balance.getAssetIssuer()));
  }

  private BigDecimal stellarBalanceToBigDecimal(final String balance)
  {
    return BigDecimal.valueOf(Double.parseDouble(balance));
  }

  private String bigDecimalToStellarBalance(final BigDecimal balance)
  {
    return balance.toString();
  }
}
