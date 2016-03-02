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
package org.mifos.module.stellar.fineractadapter;


import java.math.BigDecimal;
import java.time.LocalDate;

public class JournalEntryCommand {

  public Long officeId;
  public LocalDate transactionDate;
  public String currencyCode;
  public String comments;
  public String referenceNumber;
  public Long accountingRuleId;
  public BigDecimal amount;
  public Long paymentTypeId;
  public String accountNumber;
  public String checkNumber;
  public String receiptNumber;
  public String bankNumber;
  public String routingCode;

  public BigDecimal transactionAmount;

  public JournalEntryCommand() {
  }
}
