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
package org.fineract.module.stellar.service;

import com.google.gson.Gson;
import org.fineract.module.stellar.listener.FineractPaymentEvent;
import org.fineract.module.stellar.listener.StellarAdjustOfferEvent;
import org.fineract.module.stellar.persistencedomain.FineractPaymentEventPersistency;
import org.fineract.module.stellar.persistencedomain.PaymentPersistency;
import org.fineract.module.stellar.persistencedomain.StellarAdjustOfferEventPersistency;
import org.fineract.module.stellar.repository.FineractPaymentEventRepository;
import org.fineract.module.stellar.repository.StellarAdjustOfferEventRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UnprocessedEventObserver implements ApplicationEventPublisherAware {
  private final FineractPaymentEventRepository fineractPaymentEventRepository;
  private final Gson gson;
  private final Logger logger;
  private final StellarAdjustOfferEventRepository stellarAdjustOfferEventRepository;

  private ApplicationEventPublisher applicationEventPublisher;

  @Autowired UnprocessedEventObserver(
      final Gson gson,
      final FineractPaymentEventRepository fineractPaymentEventRepository,
      final StellarAdjustOfferEventRepository stellarAdjustOfferEventRepository,
      @Qualifier("stellarBridgeLogger")final Logger logger)
  {
    this.fineractPaymentEventRepository = fineractPaymentEventRepository;
    this.stellarAdjustOfferEventRepository = stellarAdjustOfferEventRepository;
    this.gson = gson;
    this.logger = logger;
  }

  @Scheduled(fixedRate=300000) //Once every five minutes.
  @Transactional(readOnly = true)
  void resendUnprocessedMifosPayments() {
    logger.info("Checking for and resending unprocessed payment events.");
    final Stream<FineractPaymentEventPersistency> events
        = this.fineractPaymentEventRepository.findByProcessedFalseAndOutstandingRetriesGreaterThan(0);

    events.forEach(
        event -> {
          final PaymentPersistency payment
              = gson.fromJson(event.getPayload(), PaymentPersistency.class);
          this.applicationEventPublisher.publishEvent(
              new FineractPaymentEvent(this, event.getId(), payment));
        });
  }

  @Scheduled(fixedRate=300000) //Once every five minutes.
  void resendUnprocessedStellarAdjustments() {
    logger.info("Checking for and resending unprocessed adjustments.");
    final Stream<StellarAdjustOfferEventPersistency> events
        = this.stellarAdjustOfferEventRepository.findByProcessedFalseAndOutstandingRetriesGreaterThan(0);

    events.forEach(
        event -> {
          final StellarAdjustOfferEvent adjustOfferEvent
              = new StellarAdjustOfferEvent(this, event.getId(), event.getMifosTenantId(),
              event.getAssetCode());

          this.applicationEventPublisher.publishEvent(adjustOfferEvent);
        });
  }

  //TODO: periodically cleanup the cursor repository.
  //TODO: consider cleaning up the adjustments repository periodically.

  @Override
  public void setApplicationEventPublisher
      (final ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }
}
