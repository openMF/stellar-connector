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

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stellar.base.CreateAccountOperation;
import org.stellar.base.KeyPair;
import org.stellar.base.Transaction;
import org.stellar.sdk.Account;
import org.stellar.sdk.Server;
import org.stellar.sdk.SubmitTransactionResponse;

import java.io.IOException;

@Component
public class StellarServerUtilities {

  @Value("${stellar.address}")
  private String serverAddress;

  @Value("${stellar.installation-account-private-key}")
  private String installationAccountPrivateKey;

  public java.security.KeyPair createAccount()
      throws InvalidConfigurationException, StellarAccountCreationFailedException {

    final Server server = new Server(serverAddress);
    final KeyPair installationAccountKeyPair = KeyPair.fromSecretSeed(installationAccountPrivateKey);
    final Account installationAccount = getAccount(server, installationAccountKeyPair);

    final java.security.KeyPair newTenantStellarAccountKeyPair
        = new KeyPairGenerator().generateKeyPair();

    createAccountForKeyPair(newTenantStellarAccountKeyPair, server, installationAccountKeyPair,
        installationAccount);

    return newTenantStellarAccountKeyPair;
  }

  private void createAccountForKeyPair(
      final java.security.KeyPair newAccountKeyPair,
      final Server server,
      final KeyPair installationAccountKeyPair,
      final Account installationAccount)
      throws InvalidConfigurationException, StellarAccountCreationFailedException
  {
    final Transaction.Builder transactionBuilder = new Transaction.Builder(installationAccount);

    final CreateAccountOperation createAccountOperation =
        new CreateAccountOperation.Builder(
              new KeyPair((EdDSAPublicKey) newAccountKeyPair.getPublic(),
                  (EdDSAPrivateKey) newAccountKeyPair.getPrivate()),
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
