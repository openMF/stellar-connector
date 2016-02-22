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

import org.mifos.module.stellar.federation.FederationFailedException;
import org.mifos.module.stellar.federation.InvalidStellarAddressException;
import org.mifos.module.stellar.federation.StellarAccountId;
import org.mifos.module.stellar.federation.StellarAddress;
import org.mifos.module.stellar.persistencedomain.MifosEventPersistency;
import org.mifos.module.stellar.persistencedomain.PaymentPersistency;
import org.mifos.module.stellar.repository.MifosEventRepository;
import org.mifos.module.stellar.service.HorizonServerUtilities;
import org.mifos.module.stellar.service.InvalidConfigurationException;
import org.mifos.module.stellar.service.StellarAddressResolver;
import org.mifos.module.stellar.repository.AccountBridgeRepositoryDecorator;
import org.mifos.module.stellar.service.StellarPaymentFailedException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class PaymentEventListener implements ApplicationListener<MifosPaymentEvent> {

  private final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator;
  private final MifosEventRepository mifosEventRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final StellarAddressResolver stellarAddressResolver;
  private final Logger logger;

  @Autowired
  public PaymentEventListener(
      final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator,
      final MifosEventRepository mifosEventRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final StellarAddressResolver stellarAddressResolver,
      final @Qualifier("stellarBridgeLogger")Logger logger) {
    this.accountBridgeRepositoryDecorator = accountBridgeRepositoryDecorator;
    this.mifosEventRepository = mifosEventRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.stellarAddressResolver = stellarAddressResolver;
    this.logger = logger;
  }

  @Override public void onApplicationEvent(final MifosPaymentEvent event)
      throws InvalidStellarAddressException, FederationFailedException,
      InvalidConfigurationException, StellarPaymentFailedException
  {
    final PaymentPersistency paymentPayload = event.getPayload();
    final StellarAccountId targetAccountId;
    try {
      targetAccountId =  stellarAddressResolver.getAccountIdOfStellarAccount(
          StellarAddress.forTenant(paymentPayload.targetAccount, paymentPayload.sinkDomain));
    }
    catch (final InvalidStellarAddressException | FederationFailedException ex)
    {
      logger.error("Federation failed on the address: " + paymentPayload.targetAccount);
      throw ex;
      //TODO: decide what to do with the event.  Retry? In which cases?
    }

    final char[] decodedStellarPrivateKey =
        accountBridgeRepositoryDecorator.getStellarAccountPrivateKey(paymentPayload.sourceTenantId);


    final MifosEventPersistency eventSource = this.mifosEventRepository.findOne(event.getEventId());
    try {
      horizonServerUtilities.findPathPay(
          targetAccountId,
          paymentPayload.amount, paymentPayload.assetCode,
          decodedStellarPrivateKey);
      eventSource.setProcessed(Boolean.TRUE);
      logger.info("Horizon payment processed.");
    } catch (InvalidConfigurationException | StellarPaymentFailedException ex) {
      eventSource.setProcessed(Boolean.FALSE);
      eventSource.setErrorMessage(ex.getMessage());
    }
    eventSource.setLastModifiedOn(new Date());
    this.mifosEventRepository.save(eventSource);

    //TODO: find appropriate currency for source and target.
    //TODO: adjust mifos balance
  }
}
