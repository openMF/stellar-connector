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

//import javafx.util.Pair;
import java.util.AbstractMap;
import java.util.Optional;
import org.fineract.module.stellar.federation.StellarAccountId;
import org.fineract.module.stellar.horizonadapter.HorizonServerUtilities;
import org.fineract.module.stellar.horizonadapter.StellarOfferAdjustmentFailedException;
import org.fineract.module.stellar.persistencedomain.AccountBridgePersistency;
import org.fineract.module.stellar.persistencedomain.StellarAdjustOfferEventPersistency;
import org.fineract.module.stellar.repository.AccountBridgeRepository;
import org.fineract.module.stellar.repository.StellarAdjustOfferEventRepository;
import org.fineract.module.stellar.horizonadapter.InvalidConfigurationException;
import org.fineract.module.stellar.persistencedomain.FineractPaymentEventPersistency;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StellarAdjustOfferEventListener implements
    ApplicationListener<StellarAdjustOfferEvent> {

  private final AccountBridgeRepository accountBridgeRepository;
  private final StellarAdjustOfferEventRepository stellarAdjustOfferEventRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final ValueSynchronizer<AbstractMap.SimpleEntry<String, String>> retrySynchronizer;
  private final Logger logger;

  @Autowired
  StellarAdjustOfferEventListener(
      final AccountBridgeRepository accountBridgeRepository,
      final StellarAdjustOfferEventRepository stellarAdjustOfferEventRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final @Qualifier("stellarBridgeLogger")Logger logger)
  {
    this.accountBridgeRepository = accountBridgeRepository;
    this.stellarAdjustOfferEventRepository = stellarAdjustOfferEventRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.retrySynchronizer = new ValueSynchronizer<>();
    this.logger = logger;
  }

  @Override public void onApplicationEvent(final StellarAdjustOfferEvent event) {
    final AccountBridgePersistency accountBridge =
        accountBridgeRepository.findByMifosTenantId(event.getMifosAccountId());


    retrySynchronizer.sync(new AbstractMap.SimpleEntry<>(event.getMifosAccountId(), event.getAssetCode()), () -> {
        final Optional<StellarAdjustOfferEventPersistency> existingEvent = this.stellarAdjustOfferEventRepository.findById(event.getEventId());

        final StellarAdjustOfferEventPersistency eventSource;
        if (existingEvent.isPresent()){
          
            eventSource = existingEvent.get();
            if (accountBridge == null)
            {
              eventSource.setOutstandingRetries(0);
              this.stellarAdjustOfferEventRepository.save(eventSource);
              return;
            }

            final Integer outstandingRetries = eventSource.getOutstandingRetries();
            final Boolean processed = eventSource.getProcessed();
            if (processed || (outstandingRetries <= 0))
              return;

            eventSource.setOutstandingRetries(outstandingRetries - 1);
            this.stellarAdjustOfferEventRepository.save(eventSource);

            try {
              horizonServerUtilities.adjustOffer(accountBridge.getStellarAccountPrivateKey(),
                  StellarAccountId.mainAccount(accountBridge.getStellarVaultAccountId()), event.getAssetCode());

              eventSource.setProcessed(Boolean.TRUE);
              eventSource.setErrorMessage("");
              eventSource.setOutstandingRetries(0);
            } catch (final InvalidConfigurationException |
                StellarOfferAdjustmentFailedException ex) {
              eventSource.setProcessed(Boolean.FALSE);
              eventSource.setErrorMessage(ex.getMessage());
              if (outstandingRetries == 1) {
                logger.error("Last offer adjustment attempt failed because: {}", ex.getMessage());
              }
            } finally {
              this.stellarAdjustOfferEventRepository.save(eventSource);
            }
        }
    });
  }
}
