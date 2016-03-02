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
package org.fineract.module.stellar.federation;

import com.google.common.net.InternetDomainName;
import org.fineract.module.stellar.persistencedomain.AccountBridgePersistency;
import org.fineract.module.stellar.repository.AccountBridgeRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;

@Service
public class LocalFederationService {

  private final Logger logger;

  @Value("${stellar.local-federation-domain}")
  private String federationDomain;

  private final AccountBridgeRepository accountBridgeRepository;

  @Autowired
  public LocalFederationService(
      @Qualifier("federationServerLogger") final Logger logger,
      final AccountBridgeRepository accountBridgeRepository) {
    this.logger = logger;
    this.accountBridgeRepository = accountBridgeRepository;
  }

  public boolean handlesDomain(final InternetDomainName domain) {
    final InternetDomainName federationDomainName;
    try {
      federationDomainName = InternetDomainName.from(federationDomain);
    }
    catch (final IllegalArgumentException e)
    {
      return false; //If we are not configured with a valid domain name, we don't handle this one.
    }

    return domain.equals(federationDomainName);
  }

  public StellarAccountId getAccountId(final StellarAddress stellarAddress)
      throws FederationFailedException
  {
    logger.debug("getAccountId: %s", stellarAddress);

    if (!handlesDomain(stellarAddress.getDomain())) {
      throw FederationFailedException.wrongDomain(stellarAddress.getDomain().toString());
    }

    final String tenantName = stellarAddress.getTenantName();
    final java.util.Optional<String> userAccountId = stellarAddress.getUserAccountId();

    final AccountBridgePersistency accountBridge =
        accountBridgeRepository.findByMifosTenantId(tenantName);

    if (accountBridge == null) {
      throw FederationFailedException.addressNameNotFound(stellarAddress.toString());
    }

    if (stellarAddress.isVaultAddress())
    {
      final String vaultAccountId = accountBridge.getStellarVaultAccountId();

      privateKeyIntegrityCheck(vaultAccountId, accountBridge.getStellarVaultAccountPrivateKey(),
          stellarAddress);

      return StellarAccountId.mainAccount(vaultAccountId);
    }
    else {
      final String accountId = accountBridge.getStellarAccountId();

      privateKeyIntegrityCheck(accountId, accountBridge.getStellarAccountPrivateKey(),
          stellarAddress);

      if (!userAccountId.isPresent()) {
        return StellarAccountId.mainAccount(accountId);
      } else {
        //TODO: check that an account under this user account id actually exists.
        return StellarAccountId.subAccount(accountId, userAccountId.get());
      }
    }
  }

  private void privateKeyIntegrityCheck(
      final String accountId,
      final char[] stellarAccountPrivateKey,
      final StellarAddress stellarAddress) {
    if (accountId == null || stellarAccountPrivateKey == null)
      throw FederationFailedException.addressNameNotFound(stellarAddress.toString());

    final KeyPair keyPair = KeyPair.fromSecretSeed(stellarAccountPrivateKey);
    final boolean accountHasIntegrity = keyPair.getAccountId().equals(accountId);

    if (!accountHasIntegrity) {
      throw FederationFailedException.addressNameNotFound(stellarAddress.toString());
    }
  }
}
