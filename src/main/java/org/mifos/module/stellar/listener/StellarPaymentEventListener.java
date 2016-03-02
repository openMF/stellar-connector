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

import org.mifos.module.stellar.fineractadapter.MifosBridgeAccountAdjuster;
import org.mifos.module.stellar.fineractadapter.MifosBridgeAccountAdjustmentFailedException;
import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.persistencedomain.StellarPaymentEventPersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepositoryDecorator;
import org.mifos.module.stellar.repository.StellarPaymentEventRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StellarPaymentEventListener implements ApplicationListener<StellarPaymentEvent> {
  private final StellarPaymentEventRepository stellarPaymentEventRepository;
  private final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator;
  private final MifosBridgeAccountAdjuster mifosBridgeAccountAdjuster;
  private final Logger logger;

  @Autowired
  public StellarPaymentEventListener(
      final StellarPaymentEventRepository stellarPaymentEventRepository,
      final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator,
      final MifosBridgeAccountAdjuster mifosBridgeAccountAdjuster,
      @Qualifier("stellarBridgeLogger") final Logger logger) {
    this.stellarPaymentEventRepository = stellarPaymentEventRepository;
    this.accountBridgeRepositoryDecorator = accountBridgeRepositoryDecorator;
    this.mifosBridgeAccountAdjuster = mifosBridgeAccountAdjuster;
    this.logger = logger;
  }


  @Override public void onApplicationEvent(final StellarPaymentEvent event) {

    final StellarPaymentEventPersistency eventSource = this.stellarPaymentEventRepository.findOne(event.getEventId());

    final Boolean processed = eventSource.getProcessed();
    if (processed)
      return;

    final AccountBridgePersistency bridge =
        accountBridgeRepositoryDecorator.getBridge(event.getMifosTenantId());

    try
    {
      mifosBridgeAccountAdjuster.informMifosOfIncomingStellarPayment(
          bridge.getEndpoint(),
          bridge.getMifosStagingAccount(),
          bridge.getMifosToken(),
          event.getAmount(), event.getAssetCode(), event.getEventId());

      eventSource.setProcessed(Boolean.TRUE);
      eventSource.setErrorMessage("");
      logger.info("Horizon payment processed.");
    }
    catch (MifosBridgeAccountAdjustmentFailedException ex)
    {
      eventSource.setProcessed(Boolean.FALSE);
      eventSource.setErrorMessage(ex.getMessage());
      logger.error("Payment attempt failed because \"{}\"", ex.getMessage());
    }
    finally {
      this.stellarPaymentEventRepository.save(eventSource);
    }
  }
}
