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

import java.net.URI;
import org.fineract.module.stellar.federation.StellarAccountId;
import org.fineract.module.stellar.persistencedomain.StellarCursorPersistency;
import org.fineract.module.stellar.repository.AccountBridgeRepository;
import org.fineract.module.stellar.repository.StellarCursorRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.requests.EffectsRequestBuilder;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
public class HorizonServerPaymentObserver {

  @Value("${stellar.horizon-address}")
  private String serverAddress;

  private final AccountBridgeRepository accountBridgeRepository;
  private final StellarCursorRepository stellarCursorRepository;
  private final HorizonServerEffectsListener listener;
  private final Logger logger;

  @PostConstruct
  void init() {
      
    final Optional<String> cursor = getCurrentCursor();
    accountBridgeRepository.findAll()
        .forEach(bridge -> setupListeningForAccount(
                    StellarAccountId.mainAccount(bridge.getStellarAccountId()), cursor));
  }

  @Autowired
  HorizonServerPaymentObserver(
      final AccountBridgeRepository accountBridgeRepository,
      final StellarCursorRepository stellarCursorRepository,
      final HorizonServerEffectsListener listener,
      final @Qualifier("stellarBridgeLogger") Logger logger) {
      
    this.accountBridgeRepository = accountBridgeRepository;
    this.stellarCursorRepository = stellarCursorRepository;

    this.listener = listener;
    this.logger = logger;
  }

  public void setupListeningForAccount(final StellarAccountId stellarAccountId) {
    setupListeningForAccount(stellarAccountId, Optional.empty());
  }

  private Optional<String> getCurrentCursor() {
      
    final Optional<StellarCursorPersistency> cursorPersistency = stellarCursorRepository.findTopByProcessedTrueOrderByCreatedOnDesc();
    return cursorPersistency.map(StellarCursorPersistency::getCursor);
    
  }

  private void setupListeningForAccount(
      @org.jetbrains.annotations.NotNull final StellarAccountId stellarAccountId, @org.jetbrains.annotations.NotNull final Optional<String> cursor)
  {
    logger.info("HorizonServerPaymentObserver.setupListeningForAccount {}, cursor {}",
        stellarAccountId.getPublicKey(), cursor);

    final EffectsRequestBuilder effectsRequestBuilder
        = new EffectsRequestBuilder(URI.create(serverAddress));
    effectsRequestBuilder.forAccount(KeyPair.fromAccountId(stellarAccountId.getPublicKey()));
    cursor.ifPresent(effectsRequestBuilder::cursor);

    effectsRequestBuilder.stream(listener);
  }
}
