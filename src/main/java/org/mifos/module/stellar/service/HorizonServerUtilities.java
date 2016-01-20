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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stellar.base.*;
import org.stellar.sdk.Account;
import org.stellar.sdk.Server;
import org.stellar.sdk.SubmitTransactionResponse;

import java.io.IOException;

@Component
public class HorizonServerUtilities {

  @Value("${stellar.horizon-address}")
  private String serverAddress;

  @Value("${stellar.installation-account-private-key}")
  private String installationAccountPrivateKey;
  //TODO: keeping installationAccountPrivateKey as String? Should this be removed from memory?

  /**
   * Create an account on the stellar server to be used by a Mifos tenant.  This account will
   * need a minimum balance of 20 lumens, to be derived from the installation account.
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
   * @throws StellarTrustLineCreationFailedException if the creation of the trustline failed for any
   * other reason.
   */
  void createTrustLine(
      final char[] stellarAccountPrivateKey,
      final String addressOfStellarAccountToTrust,
      final String currency,
      final long maximumAmount) throws InvalidConfigurationException, StellarTrustLineCreationFailedException {

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

    trustTransactionBuilder.addOperation(trustOperation);
    final Transaction trustTransaction = trustTransactionBuilder.build();

    trustTransaction.sign(trustingAccountKeyPair);

    try {
      final SubmitTransactionResponse createTrustLineResponse =
          server.submitTransaction(trustTransaction);
      if (!createTrustLineResponse.isSuccess())
      {
        throw StellarTrustLineCreationFailedException.trustLineTransactionFailed();
      }
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

    final CreateAccountOperation createAccountOperation =
        new CreateAccountOperation.Builder(newAccountKeyPair,
              Integer.toString(20)).
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
}
