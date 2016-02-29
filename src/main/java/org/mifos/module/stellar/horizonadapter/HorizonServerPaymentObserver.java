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
package org.mifos.module.stellar.horizonadapter;

import org.mifos.module.stellar.federation.StellarAccountId;
import org.mifos.module.stellar.persistencedomain.StellarCursorPersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepository;
import org.mifos.module.stellar.repository.StellarCursorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.requests.EffectsRequestBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Optional;

@Component
public class HorizonServerPaymentObserver {

  @Value("${stellar.horizon-address}")
  private String serverAddress;

  private final AccountBridgeRepository accountBridgeRepository;
  private final StellarCursorRepository stellarCursorRepository;
  private final HorizonServerEffectsListener listener;

  @PostConstruct
  void init()
  {
    final Optional<String> cursor = getCurrentCursor();

    accountBridgeRepository.findAll()
        .forEach(bridge -> setupListeningForAccount(
                    StellarAccountId.mainAccount(bridge.getStellarAccountId()), cursor));
  }

  @Autowired
  HorizonServerPaymentObserver(
      final AccountBridgeRepository accountBridgeRepository,
      final StellarCursorRepository stellarCursorRepository,
      final HorizonServerEffectsListener listener)
  {
    this.accountBridgeRepository = accountBridgeRepository;
    this.stellarCursorRepository = stellarCursorRepository;

    this.listener = listener;
  }

  public void setupListeningForAccount(final StellarAccountId stellarAccountId)
  {
    setupListeningForAccount(stellarAccountId, getCurrentCursor());
  }

  private Optional<String> getCurrentCursor() {
    final Optional<StellarCursorPersistency> cursorPersistency
        = stellarCursorRepository.findTopByProcessedTrueOrderByCreatedOnDesc();
    if (!cursorPersistency.isPresent())
      return Optional.empty();
    else
      return Optional.of(cursorPersistency.get().getCursor());
  }

  private void setupListeningForAccount(
      final StellarAccountId stellarAccountId, final Optional<String> cursor)
  {
    final EffectsRequestBuilder effectsRequestBuilder
        = new EffectsRequestBuilder(URI.create(serverAddress));
    effectsRequestBuilder.forAccount(KeyPair.fromAccountId(stellarAccountId.getPublicKey()));
    if (cursor.isPresent())
      effectsRequestBuilder.cursor(cursor.get());

    effectsRequestBuilder.stream(listener);
  }
}
