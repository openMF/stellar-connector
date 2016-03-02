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
package org.fineract.module.stellar.repository;


import org.fineract.module.stellar.federation.StellarAccountId;
import org.fineract.module.stellar.persistencedomain.AccountBridgePersistency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stellar.sdk.KeyPair;

@Component
public class AccountBridgeRepositoryDecorator {
  private final AccountBridgeRepository accountBridgeRepository;

  @Autowired AccountBridgeRepositoryDecorator(final AccountBridgeRepository accountBridgeRepository)
  {
    this.accountBridgeRepository = accountBridgeRepository;
  }

  public char[] getStellarAccountPrivateKey(final String mifosTenantId) {
    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (accountBridge == null)
      return null;

    return accountBridge.getStellarAccountPrivateKey();
  }

  public StellarAccountId getStellarAccountId(final String mifosTenantId) {
    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (accountBridge == null)
      return null;

    return StellarAccountId.mainAccount(accountBridge.getStellarAccountId());
  }

  public StellarAccountId getStellarVaultAccountId(final String mifosTenantId) {
    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (accountBridge == null || accountBridge.getStellarVaultAccountId() == null)
      return null;

    return StellarAccountId.mainAccount(accountBridge.getStellarVaultAccountId());
  }

  public boolean addStellarVaultAccount(
      final String mifosTenantId,
      final KeyPair stellarAccountKeyPair)
  {
    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (accountBridge == null)
      return false;

    if (accountBridge.getStellarVaultAccountPrivateKey() != null)
      return false;

    accountBridge.setStellarVaultAccountId(stellarAccountKeyPair.getAccountId());
    accountBridge.setStellarVaultAccountPrivateKey(stellarAccountKeyPair.getSecretSeed());

    this.accountBridgeRepository.save(accountBridge);
    return true;
  }

  public void save(
      final String mifosTenantId,
      final String mifosToken,
      final KeyPair accountKeyPair,
      final String endpoint,
      final String mifosStagingAccount)
  {
    final AccountBridgePersistency accountBridge =
        new AccountBridgePersistency(
            mifosTenantId,
            mifosToken,
            mifosStagingAccount,
            accountKeyPair.getAccountId(),
            accountKeyPair.getSecretSeed(),
            endpoint);

    this.accountBridgeRepository.save(accountBridge);
  }

  public boolean delete(final String mifosTenantId) {
    final AccountBridgePersistency bridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (bridge == null) {
      return false;
    }

    this.accountBridgeRepository.delete(bridge.getId());
    return true;
  }

  public AccountBridgePersistency getBridge(final String mifosTenantId)
  {
    return this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);
  }
}
