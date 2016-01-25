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
package org.mifos.module.stellar.listener;

import org.mifos.module.stellar.persistencedomain.PaymentPersistency;
import org.springframework.context.ApplicationEvent;

public class MifosPaymentEvent extends ApplicationEvent{

  private final Long eventId;

  private final PaymentPersistency payment;

  public MifosPaymentEvent(
      final Object source,
      final Long eventId,
      final PaymentPersistency payment) {
    super(source);

    this.eventId = eventId;
    this.payment = payment;
  }

  @SuppressWarnings("unused")
  public Long getEventId() {
    return eventId;
  }

  public PaymentPersistency getPayload() {
    return payment;
  }
}
