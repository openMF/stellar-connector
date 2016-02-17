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
import org.stellar.base.Asset;
import org.stellar.sdk.operations.Operation;
import org.stellar.sdk.operations.PathPaymentOperation;
import org.stellar.sdk.operations.PaymentOperation;
import org.stellar.sdk.requests.EventListener;

@Component
public class HorizonServerPaymentListener implements EventListener<Operation>{

  private final AccountBridgeRepository accountBridgeRepository;
  private final StellarCursorRepository stellarCursorRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final Logger logger;


  @Autowired
  HorizonServerPaymentListener(
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

  @Override public void onEvent(final Operation operation) {
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

  private void handleOperation(final Operation operation) {

    if (operation instanceof PaymentOperation)
    {
      final PaymentOperation paymentOperation = (PaymentOperation) operation;
      final AccountBridgePersistency toAccount
          = accountBridgeRepository.findByStellarAccountId(paymentOperation.getTo().getAccountId());
      if (toAccount == null)
        return; //Nothing to do.  Not one of ours.

      final String amount = paymentOperation.getAmount();
      final Asset asset = paymentOperation.getAsset();
      final String assetCode = HorizonServerUtilities.getAssetCode(asset);

      logger.info("Payment to {} from {} of {}, in currency {}",
          toAccount.getMifosTenantId(), paymentOperation.getFrom().getAccountId(), amount, assetCode);

      horizonServerUtilities.adjustOffer(toAccount.getStellarAccountPrivateKey(),
          StellarAccountId.mainAccount(toAccount.getStellarVaultAccountId()), asset);

      //TODO: let mifos know about the money.
      //TODO: This is a very slow approach.  Better would be to wait for multiple operations, and adjust just once.
    }
    else if (operation instanceof PathPaymentOperation)
    {
      final PathPaymentOperation pathPaymentOperation = (PathPaymentOperation)operation;

      final AccountBridgePersistency toAccount
          = accountBridgeRepository.findByStellarAccountId(pathPaymentOperation.getTo().getAccountId());
      if (toAccount == null)
        return; //Nothing to do.  Not one of ours.

      final String amount = pathPaymentOperation.getAmount();
      final Asset asset = pathPaymentOperation.getAsset();
      final String assetCode = HorizonServerUtilities.getAssetCode(asset);

      logger.info("Path payment to {} from {} of {}, in currency {}",
          toAccount.getMifosTenantId(), pathPaymentOperation.getFrom().getAccountId(), amount, assetCode);

      horizonServerUtilities.adjustOffer(toAccount.getStellarAccountPrivateKey(),
          StellarAccountId.mainAccount(toAccount.getStellarVaultAccountId()), asset);

      //TODO: let mifos know about the money.
    }
    else
    {
      logger.info("Payment operation of type {}", operation.getType());
    }
  }
}
