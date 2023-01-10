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
package org.fineract.module.stellar.listener;

import org.fineract.module.stellar.persistencedomain.PaymentPersistency;
import org.springframework.context.ApplicationEvent;

public class FineractPaymentEvent extends ApplicationEvent{

  private final Long eventId;
  
  
  public PaymentPersistency findOne(Long eventId);

  private final PaymentPersistency payment;

  public FineractPaymentEvent(
      final Object source,
      final Long eventId,
      final PaymentPersistency payment) {
    super(source);

    this.eventId = eventId;
    this.payment = payment;
  }

  public Long getEventId() {
    return eventId;
  }

  public PaymentPersistency getPayload() {
    return payment;
  }
}
