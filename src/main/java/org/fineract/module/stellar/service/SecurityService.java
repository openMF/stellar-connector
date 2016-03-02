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
package org.fineract.module.stellar.service;

import org.fineract.module.stellar.persistencedomain.AccountBridgeKeyPersistency;
import org.fineract.module.stellar.repository.AccountBridgeKeyRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.lang.*;
import java.net.URLEncoder;
import java.util.UUID;

@Service
public class SecurityService {
  private final AccountBridgeKeyRepository accountBridgeKeyRepository;
  private final Logger logger;

  @Autowired
  SecurityService(@Qualifier("stellarBridgeLogger") final Logger logger,
      final AccountBridgeKeyRepository accountBridgeRepository)
  {
    this.logger = logger;
    this.accountBridgeKeyRepository = accountBridgeRepository;
  }

  public void verifyApiKey(final String apiKey, final String mifosTenantId)
      throws SecurityException
  {
    final AccountBridgeKeyPersistency accountBridge =
        accountBridgeKeyRepository.findByMifosTenantId(mifosTenantId);
    if (accountBridge == null)
    {
      throw SecurityException.invalidTenantId(mifosTenantId);
    }
    if (!accountBridge.getRestApiKey().equals(apiKey)) {
      throw SecurityException.invalidApiKey(apiKey);
    }
  }

  public String generateApiKey(final String mifosTenantId) {

    //Check that there isn't already one.
    final AccountBridgeKeyPersistency accountBridge =
        accountBridgeKeyRepository.findByMifosTenantId(mifosTenantId);

    if (accountBridge != null)
    {
      throw SecurityException.apiKeyAlreadyExistsForTenant(mifosTenantId);
    }

    final String randomKey = UUID.randomUUID().toString();
    try
    {
      final String restApiKey = URLEncoder.encode(randomKey, "UTF-8");
      accountBridgeKeyRepository.save(new AccountBridgeKeyPersistency(
          restApiKey,
          mifosTenantId));

      return restApiKey;
    }
    catch (final UnsupportedEncodingException e)
    {
      //This should never happen.
      logger.error("Could not create API key, reason,", e);
      throw new UnexpectedException();
    }
  }

  public void removeApiKey(final String mifosTenantId) {
    accountBridgeKeyRepository.deleteByMifosTenantId(mifosTenantId);
  }
}
