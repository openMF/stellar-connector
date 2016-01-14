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

import com.google.gson.Gson;
import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepository;
import org.mifos.module.stellar.restdomain.PaymentEventPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener implements ApplicationListener<MifosPaymentEvent> {

  private final AccountBridgeRepository accountBridgeRepository;
  private final Gson gsonParser;

  @Autowired
  public PaymentEventListener(
      final AccountBridgeRepository accountBridgeRepository,
      final Gson gsonParser) {
    this.accountBridgeRepository = accountBridgeRepository;
    this.gsonParser = gsonParser;
  }

  @Override public void onApplicationEvent(final MifosPaymentEvent event) {

    final PaymentEventPayload paymentEventPayload =
        gsonParser.fromJson(event.getPayload(), PaymentEventPayload.class);

    final AccountBridgePersistency accountBridgePersistency =
        accountBridgeRepository.findByMifosTenantId(event.getTenantId());



  }
}
