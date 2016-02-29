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
package org.mifos.module.stellar.controller;

import com.google.common.base.Preconditions;
import org.mifos.module.stellar.persistencedomain.PaymentPersistency;
import org.mifos.module.stellar.restdomain.JournalEntryData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class JournalEntryPaymentMapper {

  public static final String STELLAR_ROUTING_CODE = "STELLAR";

  PaymentPersistency mapToPayment (
      final String mifosTenantId,
      final JournalEntryData journalEntryData)  throws InvalidJournalEntryException {

    Preconditions.checkNotNull(journalEntryData);
    if ((journalEntryData.transactionDetails == null) ||
        (journalEntryData.currency == null) ||
        (journalEntryData.amount == null) ||
        (journalEntryData.transactionDetails.paymentDetails == null) ||
        (journalEntryData.transactionDetails.paymentDetails.routingCode == null) ||
        (journalEntryData.transactionDetails.paymentDetails.accountNumber == null) ||
        (journalEntryData.transactionDetails.paymentDetails.bankNumber == null) ||
        (journalEntryData.currency.inMultiplesOf == null) ||
        (journalEntryData.currency.code == null))
    {
      throw new InvalidJournalEntryException();
    }

    final PaymentPersistency ret = new PaymentPersistency();

    //TODO: it's unclear if any of this is correct. Ask Adi.
    ret.assetCode = journalEntryData.currency.code;
    ret.amount =
        journalEntryData.amount.multiply(
            BigDecimal.valueOf(journalEntryData.currency.inMultiplesOf));
    ret.sourceTenantId = mifosTenantId;
    ret.targetAccount = journalEntryData.transactionDetails.paymentDetails.accountNumber;
    ret.sinkDomain = journalEntryData.transactionDetails.paymentDetails.bankNumber;
    ret.isStellarPayment = (journalEntryData.transactionDetails.paymentDetails.routingCode.equals(
        STELLAR_ROUTING_CODE));
    ret.targetSubAccount = "";


    return ret;
  }

}
