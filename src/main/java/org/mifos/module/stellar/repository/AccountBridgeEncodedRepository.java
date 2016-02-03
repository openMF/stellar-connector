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
package org.mifos.module.stellar.repository;


import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AccountBridgeEncodedRepository {
  private final AccountBridgeRepository accountBridgeRepository;

  @Autowired
  AccountBridgeEncodedRepository(final AccountBridgeRepository accountBridgeRepository)
  {
    this.accountBridgeRepository = accountBridgeRepository;
  }

  public char[] encode(final char[] secretSeed) {
    return secretSeed; //TODO:
  }

  public char[] getPrivateKey(final String mifosTenantId) {
    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);
    //TODO: zeroing?
    return decode(accountBridge.getStellarAccountPrivateKey());
  }


  private char[] decode(final char[] stellarAccountPrivateKey) {
    return stellarAccountPrivateKey; //TODO:
  }

  public void save(
      final String mifosTenantId,
      final String mifosToken,
      final String accountId,
      final char[] secretSeed)
  {
    //TODO: does anything need to be removed from memory here?
    final char[] encodedSecretSeed
        = this.encode(secretSeed);

    final AccountBridgePersistency accountBridge =
        new AccountBridgePersistency(
            mifosTenantId,
            mifosToken,
            accountId,
            encodedSecretSeed);

    this.accountBridgeRepository.save(accountBridge);
  }

  public boolean delete(String mifosTenantId) {
    final AccountBridgePersistency bridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (bridge == null) {
      return false;
    }

    this.accountBridgeRepository.delete(bridge.getId());
    return true;
  }
}
