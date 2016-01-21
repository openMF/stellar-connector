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

import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

@Service
public class SecurityService {
  private final AccountBridgeRepository accountBridgeRepository;
  private final Logger logger;

  @Autowired
  SecurityService(@Qualifier("stellarBridgeLogger") final Logger logger,
      final AccountBridgeRepository accountBridgeRepository)
  {
    this.logger = logger;
    this.accountBridgeRepository = accountBridgeRepository;
  }

  public void verifyApiKey(final String apiKey, final String mifosTenantId)
      throws InvalidApiKeyException
  {
    try (final AccountBridgePersistency accountBridge =
        accountBridgeRepository.findByMifosTenantId(mifosTenantId))
    {
      if (!accountBridge.getRestApiKey().equals(apiKey)) {
        throw new InvalidApiKeyException(apiKey);
      }
    }
  }

  public String generateApiKey() {
    final String randomKey = UUID.randomUUID().toString();
    try
    {
      return URLEncoder.encode(randomKey, "UTF-8");
    }
    catch (final UnsupportedEncodingException e)
    {
      //This should never happen.
      logger.error("Could not create API key, reason,", e);
      throw new UnexpectedException();
    }
  }
}
