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

import org.fineract.module.stellar.federation.*;
import org.fineract.module.stellar.fineractadapter.Adapter;
import org.fineract.module.stellar.fineractadapter.FineractBridgeAccountAdjustmentFailedException;
import org.fineract.module.stellar.horizonadapter.HorizonServerUtilities;
import org.fineract.module.stellar.horizonadapter.InvalidConfigurationException;
import org.fineract.module.stellar.persistencedomain.AccountBridgePersistency;
import org.fineract.module.stellar.persistencedomain.FineractPaymentEventPersistency;
import org.fineract.module.stellar.persistencedomain.PaymentPersistency;
import org.fineract.module.stellar.repository.AccountBridgeRepositoryDecorator;
import org.fineract.module.stellar.repository.FineractPaymentEventRepository;
import org.fineract.module.stellar.horizonadapter.StellarPaymentFailedException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class FineractPaymentEventListener implements ApplicationListener<FineractPaymentEvent> {

  private final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator;
  private final FineractPaymentEventRepository fineractPaymentEventRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final StellarAddressResolver stellarAddressResolver;
  private final Adapter adapter;
  private final Logger logger;
  private final ValueSynchronizer<Long> retrySynchronizer;


  @Autowired
  public FineractPaymentEventListener(
      final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator,
      final FineractPaymentEventRepository fineractPaymentEventRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final StellarAddressResolver stellarAddressResolver,
      final Adapter adapter,
      final @Qualifier("stellarBridgeLogger")Logger logger) {
    this.accountBridgeRepositoryDecorator = accountBridgeRepositoryDecorator;
    this.fineractPaymentEventRepository = fineractPaymentEventRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.stellarAddressResolver = stellarAddressResolver;
    this.adapter = adapter;
    this.logger = logger;
    this.retrySynchronizer = new ValueSynchronizer<>();
  }

  @Override public void onApplicationEvent(final FineractPaymentEvent event)
      throws InvalidStellarAddressException, FederationFailedException,
      InvalidConfigurationException, StellarPaymentFailedException
  {
    retrySynchronizer.sync(event.getEventId(), () ->
    {
      final FineractPaymentEventPersistency eventSource = this.fineractPaymentEventRepository.findOne(event.getEventId());

      final Integer outstandingRetries = eventSource.getOutstandingRetries();
      final Boolean processed = eventSource.getProcessed();
      if (processed || (outstandingRetries <= 0))
        return;

      eventSource.setOutstandingRetries(outstandingRetries - 1);
      eventSource.setLastModifiedOn(new Date());
      this.fineractPaymentEventRepository.save(eventSource);


      final PaymentPersistency paymentPayload = event.getPayload();

      final AccountBridgePersistency bridge =
          accountBridgeRepositoryDecorator.getBridge(paymentPayload.sourceTenantId);



      try
      {
        final StellarAccountId targetAccountId;
        targetAccountId =  stellarAddressResolver.getAccountIdOfStellarAccount(
            StellarAddress.forTenant(paymentPayload.targetAccount, paymentPayload.sinkDomain));

        final char[] decodedStellarPrivateKey =
            accountBridgeRepositoryDecorator.getStellarAccountPrivateKey(paymentPayload.sourceTenantId);

        horizonServerUtilities.findPathPay(
            targetAccountId,
            paymentPayload.amount, paymentPayload.assetCode,
            decodedStellarPrivateKey);

        eventSource.setOutstandingRetries(0); //Set retries to 0 before telling Mifos, in case something goes wrong.
        this.fineractPaymentEventRepository.save(eventSource);

        adapter.tellMifosPaymentSucceeded(bridge.getEndpoint(),
            bridge.getMifosStagingAccount(), event.getEventId(), paymentPayload.assetCode,
            paymentPayload.amount);

        eventSource.setProcessed(Boolean.TRUE);
        eventSource.setErrorMessage("");
        logger.info("Horizon payment processed.");
      }
      catch (
          final InvalidConfigurationException |
              StellarPaymentFailedException |
              FederationFailedException |
              FineractBridgeAccountAdjustmentFailedException ex)
      {
        //TODO: figure out how to communicate missing funds problem to user.
        eventSource.setProcessed(Boolean.FALSE);
        eventSource.setErrorMessage(ex.getMessage());
        logger.error("Payment attempt failed because \"{}\", retries remaining: {}",
            ex.getMessage(), outstandingRetries);
      }
      catch (final InvalidStellarAddressException ex)
      {
        eventSource.setProcessed(Boolean.FALSE);
        eventSource.setErrorMessage(ex.getMessage());
        eventSource.setOutstandingRetries(0);
        logger.error("Invalid stellar address: {}", paymentPayload.targetAccount);
      }
      finally {
        eventSource.setLastModifiedOn(new Date());
        this.fineractPaymentEventRepository.save(eventSource);
      }
    });
  }
}
