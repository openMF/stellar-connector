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

import org.fineract.module.stellar.listener.StellarAdjustOfferEvent;
import org.fineract.module.stellar.listener.StellarPaymentEvent;
import org.fineract.module.stellar.persistencedomain.AccountBridgePersistency;
import org.fineract.module.stellar.persistencedomain.StellarAdjustOfferEventPersistency;
import org.fineract.module.stellar.persistencedomain.StellarCursorPersistency;
import org.fineract.module.stellar.persistencedomain.StellarPaymentEventPersistency;
import org.fineract.module.stellar.repository.AccountBridgeRepository;
import org.fineract.module.stellar.repository.StellarAdjustOfferEventRepository;
import org.fineract.module.stellar.repository.StellarCursorRepository;
import org.fineract.module.stellar.repository.StellarPaymentEventRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;
import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;
import org.stellar.sdk.requests.EventListener;
import org.stellar.sdk.responses.effects.AccountCreditedEffectResponse;
import org.stellar.sdk.responses.effects.AccountDebitedEffectResponse;
import org.stellar.sdk.responses.effects.EffectResponse;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

@Component
public class HorizonServerEffectsListener implements EventListener<EffectResponse>,
    ApplicationEventPublisherAware {

  private static final Integer ADJUSTOFFER_PROCESSING_MAXIMUM_RETRY_COUNT = 3;
  private final AccountBridgeRepository accountBridgeRepository;
  private final StellarCursorRepository stellarCursorRepository;
  private final StellarAdjustOfferEventRepository stellarAdjustOfferEventRepository;
  private final StellarPaymentEventRepository stellarPaymentEventRepository;
  private ApplicationEventPublisher eventPublisher;
  private final Logger logger;


  @Autowired HorizonServerEffectsListener(
      final AccountBridgeRepository accountBridgeRepository,
      final StellarCursorRepository stellarCursorRepository,
      final StellarAdjustOfferEventRepository stellarAdjustOfferEventRepository,
      final StellarPaymentEventRepository stellarPaymentEventRepository,
      final @Qualifier("stellarBridgeLogger") Logger logger)
  {
    this.accountBridgeRepository = accountBridgeRepository;
    this.stellarCursorRepository = stellarCursorRepository;
    this.stellarAdjustOfferEventRepository = stellarAdjustOfferEventRepository;
    this.stellarPaymentEventRepository = stellarPaymentEventRepository;
    this.logger = logger;
  }

  @Override
  public void setApplicationEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override public void onEvent(final EffectResponse operation) {
    final String pagingToken = operation.getPagingToken();

    final StellarCursorPersistency cursorPersistency = markPlace(pagingToken);
    if (cursorPersistency.getProcessed())
      return;

    logger.info("Operation with cursor {}", pagingToken);

    handleOperation(operation);

    cursorPersistency.setProcessed(true);
    stellarCursorRepository.save(cursorPersistency);
  }

  StellarCursorPersistency markPlace(final String pagingToken)
  {
    synchronized (stellarCursorRepository) {
      final Optional<StellarCursorPersistency> entry =
          stellarCursorRepository.findByCursor(pagingToken);

      return entry.orElse(
          stellarCursorRepository.save(new StellarCursorPersistency(pagingToken, new Date())));
    }
  }

  private void handleOperation(final EffectResponse effect) {

    if (effect instanceof AccountCreditedEffectResponse)
    {
      final AccountCreditedEffectResponse accountCreditedEffect = (AccountCreditedEffectResponse) effect;
      final AccountBridgePersistency toAccount
          = accountBridgeRepository.findByStellarAccountId(effect.getAccount().getAccountId());
      if (toAccount == null)
        return; //Nothing to do.  Not one of ours.

      final BigDecimal amount
          = StellarAccountHelpers.stellarBalanceToBigDecimal(accountCreditedEffect.getAmount());
      final Asset asset = accountCreditedEffect.getAsset();
      final String assetCode = StellarAccountHelpers.getAssetCode(asset);
      final String issuer = StellarAccountHelpers.getIssuer(asset);

      logger.info("Credit to {} of {}, in currency {}@{}",
          toAccount.getMifosTenantId(), amount, assetCode, issuer);

      //TODO: This will prevent lumens from being registered in the mifos account (likewise below in debit)...
      if (!(asset instanceof AssetTypeCreditAlphaNum))
        return;


      if (toAccount.getStellarVaultAccountId() != null) { //Only adjust offers if has a vault account.
        final Long adjustmentEventId =
            this.saveAdjustmentEvent(toAccount.getMifosTenantId(), assetCode);

        this.eventPublisher.publishEvent(
            new StellarAdjustOfferEvent(this, adjustmentEventId, toAccount.getMifosTenantId(), assetCode));
      }

      final Long stellarPaymentEventId
          = this.savePaymentEvent(toAccount.getMifosTenantId(), assetCode, amount);


      this.eventPublisher.publishEvent(
          new StellarPaymentEvent(this, stellarPaymentEventId, toAccount.getMifosTenantId(),
              assetCode, amount));
    }
    else if (effect instanceof AccountDebitedEffectResponse)
    {
      final AccountDebitedEffectResponse accountDebitedEffect = (AccountDebitedEffectResponse)effect;

      final AccountBridgePersistency toAccount = accountBridgeRepository
          .findByStellarAccountId(accountDebitedEffect.getAccount().getAccountId());
      if (toAccount == null)
        return; //Nothing to do.  Not one of ours.

      final BigDecimal amount
          = StellarAccountHelpers.stellarBalanceToBigDecimal(accountDebitedEffect.getAmount());
      final Asset asset = accountDebitedEffect.getAsset();
      final String assetCode = StellarAccountHelpers.getAssetCode(asset);
      final String issuer = StellarAccountHelpers.getIssuer(asset);

      logger.info("Debit to {} of {}, in currency {}@{}",
          toAccount.getMifosTenantId(), amount, assetCode, issuer);

      if (!(asset instanceof AssetTypeCreditAlphaNum))
        return;

      if (toAccount.getStellarVaultAccountId() != null) { //Only adjust offers if has a vault account.
        final Long adjustmentEventId =
            this.saveAdjustmentEvent(toAccount.getMifosTenantId(), assetCode);

        this.eventPublisher.publishEvent(
            new StellarAdjustOfferEvent(this, adjustmentEventId, toAccount.getMifosTenantId(), assetCode));
      }

      final Long stellarPaymentEventId
          = this.savePaymentEvent(toAccount.getMifosTenantId(), assetCode, amount);


      this.eventPublisher.publishEvent(
          new StellarPaymentEvent(this, stellarPaymentEventId, toAccount.getMifosTenantId(),
              assetCode, amount));
    }
    else
    {
      logger.info("Effect of type {}", effect.getType());
    }
  }

  private Long savePaymentEvent(
      final String mifosTenantId,
      final String assetCode,
      final BigDecimal amount) {
    final StellarPaymentEventPersistency eventSource = new StellarPaymentEventPersistency();
    eventSource.setMifosTenantId(mifosTenantId);
    eventSource.setAssetCode(assetCode);
    eventSource.setAmount(amount);
    eventSource.setProcessed(Boolean.FALSE);
    eventSource.setErrorMessage("");
    eventSource.setCreatedOn(new Date());

    return this.stellarPaymentEventRepository.save(eventSource).getId();
  }

  synchronized private Long saveAdjustmentEvent(final String mifosTenantId, final String assetCode) {
    final Optional<StellarAdjustOfferEventPersistency> existingEvent =
        this.stellarAdjustOfferEventRepository
            .findAnyByProcessedFalseAndOutstandingRetriesGreaterThanAndMifosTenantIdEqualsAndAssetCodeEquals(0, mifosTenantId, assetCode);

    final StellarAdjustOfferEventPersistency eventSource;
    if (existingEvent.isPresent())
    {
      eventSource = existingEvent.get();
      eventSource.setOutstandingRetries(ADJUSTOFFER_PROCESSING_MAXIMUM_RETRY_COUNT);

    }
    else {
      eventSource = new StellarAdjustOfferEventPersistency();
      eventSource.setMifosTenantId(mifosTenantId);
      eventSource.setAssetCode(assetCode);
      eventSource.setProcessed(Boolean.FALSE);
      eventSource.setOutstandingRetries(ADJUSTOFFER_PROCESSING_MAXIMUM_RETRY_COUNT);
      eventSource.setErrorMessage("");
      eventSource.setCreatedOn(new Date());
    }

    return this.stellarAdjustOfferEventRepository.save(eventSource).getId();
  }
}
