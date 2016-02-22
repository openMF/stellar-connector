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
package org.mifos.module.stellar.service;

import org.mifos.module.stellar.federation.StellarAccountId;
import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.persistencedomain.StellarCursorPersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepository;
import org.mifos.module.stellar.repository.StellarCursorRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.stellar.sdk.Asset;
import org.stellar.sdk.requests.EventListener;
import org.stellar.sdk.responses.effects.AccountCreditedEffectResponse;
import org.stellar.sdk.responses.effects.AccountDebitedEffectResponse;
import org.stellar.sdk.responses.effects.EffectResponse;

@Component
public class HorizonServerEffectsListener implements EventListener<EffectResponse> {

  private final AccountBridgeRepository accountBridgeRepository;
  private final StellarCursorRepository stellarCursorRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final Logger logger;


  @Autowired HorizonServerEffectsListener(
      final AccountBridgeRepository accountBridgeRepository,
      final StellarCursorRepository stellarCursorRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final @Qualifier("stellarBridgeLogger") Logger logger)
  {
    this.accountBridgeRepository = accountBridgeRepository;
    this.stellarCursorRepository = stellarCursorRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.logger = logger;
  }

  @Override public void onEvent(final EffectResponse operation) {
    final String pagingToken = operation.getPagingToken();

    final StellarCursorPersistency cursorPersistency = markPlace(pagingToken);
    if (cursorPersistency == null)
      return;

    logger.info("Operation with cursor {}", pagingToken);

    handleOperation(operation);

    cursorPersistency.setProcessed(true);
    stellarCursorRepository.save(cursorPersistency);
  }

  synchronized
  StellarCursorPersistency markPlace(final String pagingToken)
  {
    final StellarCursorPersistency entry = stellarCursorRepository.findByCursor(pagingToken);
    if (entry != null)
      return null;

    return stellarCursorRepository.save(new StellarCursorPersistency(pagingToken));
  }

  private void handleOperation(final EffectResponse effect) {

    if (effect instanceof AccountCreditedEffectResponse)
    {
      final AccountCreditedEffectResponse accountCreditedEffect = (AccountCreditedEffectResponse) effect;
      final AccountBridgePersistency toAccount
          = accountBridgeRepository.findByStellarAccountId(effect.getAccount().getAccountId());
      if (toAccount == null)
        return; //Nothing to do.  Not one of ours.

      final String amount = accountCreditedEffect.getAmount();
      final Asset asset = accountCreditedEffect.getAsset();
      final String assetCode = StellarAccountHelpers.getAssetCode(asset);
      final String issuer = StellarAccountHelpers.getIssuer(asset);

      logger.info("Credit to {} of {}, in currency {}@{}",
          toAccount.getMifosTenantId(), amount, assetCode, issuer);

      horizonServerUtilities.adjustOffer(toAccount.getStellarAccountPrivateKey(),
          StellarAccountId.mainAccount(toAccount.getStellarVaultAccountId()), asset);

      //TODO: let mifos know about the money.
      //TODO: This is a very slow approach.  Better would be to wait for multiple operations, and adjust just once.
      //TODO: In case stellar transaction fails, should not be throwing runtime exceptions back at stellar.
    }
    else if (effect instanceof AccountDebitedEffectResponse)
    {
      final AccountDebitedEffectResponse accountDebitedEffect = (AccountDebitedEffectResponse)effect;

      final AccountBridgePersistency toAccount = accountBridgeRepository
          .findByStellarAccountId(accountDebitedEffect.getAccount().getAccountId());
      if (toAccount == null)
        return; //Nothing to do.  Not one of ours.

      final String amount = accountDebitedEffect.getAmount();
      final Asset asset = accountDebitedEffect.getAsset();
      final String assetCode = StellarAccountHelpers.getAssetCode(asset);
      final String issuer = StellarAccountHelpers.getIssuer(asset);

      logger.info("Debit to {} of {}, in currency {}@{}",
          toAccount.getMifosTenantId(), amount, assetCode, issuer);

      horizonServerUtilities.adjustOffer(toAccount.getStellarAccountPrivateKey(),
          StellarAccountId.mainAccount(toAccount.getStellarVaultAccountId()), asset);

      //TODO: let mifos know about the money.
    }
    else
    {
      logger.info("Effect of type {}", effect.getType());
    }
  }
}
