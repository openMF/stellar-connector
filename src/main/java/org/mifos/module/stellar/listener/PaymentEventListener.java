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
import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.persistencedomain.PaymentPersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepository;
import org.mifos.module.stellar.service.HorizonServerUtilities;
import org.mifos.module.stellar.service.StellarAddressResolver;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener implements ApplicationListener<MifosPaymentEvent> {

  private final AccountBridgeRepository accountBridgeRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final StellarAddressResolver stellarAddressResolver;
  private final Logger logger;

  @Autowired
  public PaymentEventListener(
      final AccountBridgeRepository accountBridgeRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final StellarAddressResolver stellarAddressResolver,
      final @Qualifier("stellarBridgeLogger")Logger logger) {
    this.accountBridgeRepository = accountBridgeRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.stellarAddressResolver = stellarAddressResolver;
    this.logger = logger;
  }

  @Override public void onApplicationEvent(final MifosPaymentEvent event)
      throws InvalidStellarAddressException, FederationFailedException
  {
    final PaymentPersistency paymentPayload = event.getPayload();
    final StellarAccountId targetAccountId;
    try {
      targetAccountId =  stellarAddressResolver.getAccountIdOfStellarAccount(StellarAddress.parse(paymentPayload.targetAccount));
    }
    catch (final InvalidStellarAddressException | FederationFailedException ex)
    {
      logger.error("Federation failed on the address: " + paymentPayload.targetAccount);
      throw ex;
      //TODO: decide what to do with the event.  Retry? In which cases?
    }

    try (final AccountBridgePersistency accountBridge =
        accountBridgeRepository.findByMifosTenantId(paymentPayload.sourceTenantId))
    {
      horizonServerUtilities.pay(
          targetAccountId,
          paymentPayload.amount,
          paymentPayload.assetCode,
          accountBridge.getStellarAccountPrivateKey());

      //TODO: adjust mifos balance
      //TODO: Mark event as processed
    }
  }
}
