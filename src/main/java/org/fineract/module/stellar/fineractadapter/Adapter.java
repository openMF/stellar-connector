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
package org.fineract.module.stellar.fineractadapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
//import retrofit.RestAdapter;
//import retrofit.RetrofitError;

import java.math.BigDecimal;
import retrofit2.Retrofit;

@Component
public class Adapter {
  public static final int STELLAR_TRANSFER_ACCOUNT = 1; //TODO: ???
  private final RestAdapterProvider restAdapterProvider;

  @Autowired Adapter(final RestAdapterProvider restAdapterProvider)
  {
    this.restAdapterProvider = restAdapterProvider;
  }

  public void tellMifosPaymentSucceeded(
      final String endpoint,
      final String mifosStagingAccount,
      final Long eventId,
      final String assetCode,
      final BigDecimal amount) throws FineractBridgeAccountAdjustmentFailedException
  {
    try {
      final Retrofit restAdapter = this.restAdapterProvider.get(endpoint);
      /*final Retrofit restAdapter = new Retrofit.Builder()
                            .baseUrl(endpoint)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();*/
      
      final FineractClientService clientService = restAdapter.create(FineractClientService.class);

      final JournalEntryCommand command = new JournalEntryCommand();
      command.accountNumber = mifosStagingAccount;
      command.transactionAmount = amount;
      command.currencyCode = assetCode;
      command.receiptNumber = String.valueOf(eventId);

      //TODO: shouldn't the mifos token come into play here?

      clientService.createSavingsAccountTransaction(STELLAR_TRANSFER_ACCOUNT, "withdrawal", command);
    }
    catch (final RetrofitError ex)
    {
      throw new FineractBridgeAccountAdjustmentFailedException(ex.getResponse().getReason());
    }
  }

  public void informMifosOfIncomingStellarPayment(
      final String endpoint,
      final String mifosStagingAccount,
      final String mifosToken,
      final BigDecimal amount,
      final String assetCode,
      final Long eventId) throws FineractBridgeAccountAdjustmentFailedException
  {
    try {
      final RestAdapter restAdapter = this.restAdapterProvider.get(endpoint);
      final FineractClientService clientService = restAdapter.create(FineractClientService.class);

      final JournalEntryCommand command = new JournalEntryCommand();
      command.accountNumber = mifosStagingAccount;
      command.transactionAmount = amount;
      command.currencyCode = assetCode;
      command.receiptNumber = String.valueOf(eventId);

      //TODO: shouldn't the mifos token come into play here?

      clientService.createSavingsAccountTransaction(STELLAR_TRANSFER_ACCOUNT, "deposit", command);
    }
    catch (final RetrofitError ex)
    {
      throw new FineractBridgeAccountAdjustmentFailedException(ex.getResponse().getReason());
    }
  }

  public boolean accountExists(final String userAccount) {

    //TODO: check that an account under this user account id actually exists.
    return false;
  }

  public String createStagingAccount(String endpoint, String mifosTenantId, String mifosToken) {
    return "placeholder"; //TODO:

  }

  public void removeStagingAccount(
      final String endpoint,
      final String mifosTenantId,
      final String mifosToken,
      final String mifosStagingAccount) {
    //TODO:
  }
}
