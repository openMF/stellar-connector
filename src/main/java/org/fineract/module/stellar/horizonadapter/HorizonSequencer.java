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

import org.slf4j.Logger;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;

public class HorizonSequencer {

  interface TransactionFailedException<T extends Exception> {
    T exceptionWhenTransactionFails();
  }

  private final Account account;

  HorizonSequencer(final KeyPair keyPair, final Long sequenceNumber)
  {
    account = new Account(keyPair, sequenceNumber);
  }

  public TransactionBuilderAccount getAccount() {
    return account;
  }

  synchronized <T extends Exception> void submitTransaction(
      final Server server,
      final Transaction.Builder transactionBuilder,
      final KeyPair signingKeyPair,
      final Logger logger,
      final TransactionFailedException<RuntimeException> failureHandler) throws T, IOException
  {
    final Transaction transaction = transactionBuilder.build();
    //final Long sequenceNumberSubmitted = account.getSequenceNumber();
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
      throw failureHandler.exceptionWhenTransactionFails();
    }
  }
}
