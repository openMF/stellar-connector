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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

@Component
public class HorizonServerUtilities {

  private final Logger logger;

  @Value("${stellar.horizon-address}")
  private String serverAddress;

  @Value("${stellar.installation-account-private-key}")
  private String installationAccountPrivateKey;
  //TODO: keeping installationAccountPrivateKey as String? Should this be removed from memory?

  @Value("${stellar.new-account-initial-balance}")
  private int initialBalance = 20;

  @Autowired
  HorizonServerUtilities(@Qualifier("stellarBridgeLogger")final Logger logger)
  {
    this.logger = logger;
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
  KeyPair createAccount()
      throws InvalidConfigurationException, StellarAccountCreationFailedException {

    final Server server = new Server(serverAddress);
    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);
    final Account installationAccount = getAccount(server, installationAccountKeyPair);

    final KeyPair newTenantStellarAccountKeyPair = KeyPair.random();

    createAccountForKeyPair(newTenantStellarAccountKeyPair, server, installationAccountKeyPair,
        installationAccount);

    //TODO: setup inflation voting for the new account.

    return newTenantStellarAccountKeyPair;
  }

  /**
   * Creates a line of trust between stellar accounts for one currency, and up to a maximum amount.
   *
   * @param stellarAccountPrivateKey the key of the account doing the trusting
   * @param addressOfStellarAccountToTrust the account Id of the account to be trusted.
   * @param currency the currency symbol of the currency to be trusted.  See
   *                 https://www.stellar.org/developers/learn/concepts/assets.html
   *                 for a description of how to create a valid asset code.
   * @param maximumAmount the maximum amount of the currency to be trusted.
   *
   * @throws InvalidConfigurationException if the horizon server named in the configuration cannot
   * be reached.  Either the address is wrong or the horizon server named is't running, or there is
   * a problem with the network.
   * @throws StellarCreditLineCreationFailedException if the creation of the trustline failed for any
   * other reason.
   */
  void createCreditLine(
      final char[] stellarAccountPrivateKey,
      final String addressOfStellarAccountToTrust,
      final String currency,
      final long maximumAmount)
      throws InvalidConfigurationException, StellarCreditLineCreationFailedException
  {

    final Server server = new Server(serverAddress);

    final KeyPair trustingAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final Account trustingAccount = getAccount(server, trustingAccountKeyPair);

    final Transaction.Builder trustTransactionBuilder =
        new Transaction.Builder(trustingAccount);

    final KeyPair keyPairOfStellarAccountToTrust
        = KeyPair.fromAccountId(addressOfStellarAccountToTrust);

    final Asset assetToTrust = Asset.createNonNativeAsset(currency, keyPairOfStellarAccountToTrust);

    final ChangeTrustOperation trustOperation =
        new ChangeTrustOperation.Builder(assetToTrust, Long.toString(maximumAmount)).build();

    final Asset inverseAsset = Asset.createNonNativeAsset(currency, trustingAccountKeyPair);

    final ManageOfferOperation offerOperation =
        new ManageOfferOperation.Builder(inverseAsset, assetToTrust,
            Long.toString(maximumAmount), Long.toString(maximumAmount)).build();

    trustTransactionBuilder.addOperation(trustOperation);
    trustTransactionBuilder.addOperation(offerOperation);

    final Transaction trustTransaction = trustTransactionBuilder.build();

    trustTransaction.sign(trustingAccountKeyPair);

    try {
      final SubmitTransactionResponse createTrustLineResponse =
          server.submitTransaction(trustTransaction);
      if (!createTrustLineResponse.isSuccess())
      {
        throw StellarCreditLineCreationFailedException.trustLineTransactionFailed();
      }
      //TODO: find a way to communicate back fees
    } catch (IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
  }

  private void createAccountForKeyPair(
      final KeyPair newAccountKeyPair,
      final Server server,
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
            setSourceAccount(installationAccountKeyPair).build();

    transactionBuilder.addOperation(createAccountOperation);

    final Transaction createAccountTransaction = transactionBuilder.build();

    createAccountTransaction.sign(installationAccountKeyPair);

    try {
      final SubmitTransactionResponse createAccountResponse = server.submitTransaction(createAccountTransaction);
      if (!createAccountResponse.isSuccess())
      {
        throw new StellarAccountCreationFailedException();
      }
    } catch (IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
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

  public void pay(
      final StellarAccountId targetAccountId,
      final BigDecimal amount,
      final String assetCode,
      final char[] stellarAccountPrivateKey)
      throws InvalidConfigurationException, StellarPaymentFailedException
  {
    final Server server = new Server(serverAddress);
    final KeyPair sourceAccountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final KeyPair targetAccountKeyPair = KeyPair.fromAccountId(targetAccountId.getPublicKey());

    final Asset sendAsset = Asset.createNonNativeAsset(assetCode, sourceAccountKeyPair);
    final Asset receiveAsset = Asset.createNonNativeAsset(assetCode, targetAccountKeyPair);

    final Account sourceAccount;
    final BigDecimal balanceOfAssetCreditsFromTarget;
    try {
      sourceAccount = server.accounts().account(sourceAccountKeyPair);
      balanceOfAssetCreditsFromTarget = getBalanceOfCreditsInAsset(sourceAccount, receiveAsset);

    }
    catch (final IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }

    final Transaction.Builder transferTransactionBuilder = new Transaction.Builder(sourceAccount);
    final PathPaymentOperation paymentOperation =
        new PathPaymentOperation.Builder(
            sendAsset,
            bigDecimalToStellarBalance(amount), //TODO:???
            targetAccountKeyPair,
            receiveAsset,
            bigDecimalToStellarBalance(amount))
            .setSourceAccount(sourceAccountKeyPair).build();

    transferTransactionBuilder.addOperation(paymentOperation);

    if (!balanceOfAssetCreditsFromTarget.equals(BigDecimal.ZERO))
    {
      final ManageOfferOperation tradeOfferOperation =
          new ManageOfferOperation.Builder(
              receiveAsset,
              sendAsset,
              bigDecimalToStellarBalance(balanceOfAssetCreditsFromTarget),
              bigDecimalToStellarBalance(balanceOfAssetCreditsFromTarget))
              .setSourceAccount(sourceAccountKeyPair)
              .build();
      transferTransactionBuilder.addOperation(tradeOfferOperation);
    }

    if (targetAccountId.getSubAccount().isPresent())
    {
      final Memo subAccountMemo = Memo.text(targetAccountId.getSubAccount().get());
      transferTransactionBuilder.addMemo(subAccountMemo);
    }

    final Transaction transferTransaction = transferTransactionBuilder.build();
    transferTransaction.sign(sourceAccountKeyPair);


    try {
      final SubmitTransactionResponse paymentResponse = server.submitTransaction(transferTransaction);
      if (!paymentResponse.isSuccess())
      {
        throw new StellarPaymentFailedException();
      }
      //TODO: find a way to communicate back fees
    } catch (IOException e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress(serverAddress);
    }
  }

  public BigDecimal getBalance(
      final char[] stellarAccountPrivateKey,
      final String assetCode)
  {
    final Server server = new Server(serverAddress);
    final KeyPair accountKeyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);

    final Account tenantAccount = getAccount(server, accountKeyPair);
    final Account.Balance[] balances = tenantAccount.getBalances();

    return Arrays.asList(balances).stream()
        .filter(balance -> balanceIsInAsset(balance, assetCode))
        .map(balance -> stellarBalanceToBigDecimal(balance.getBalance()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal getBalanceOfCreditsInAsset(
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
        KeyPair.fromSecretSeed(balance.getAssetIssuer()));
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
