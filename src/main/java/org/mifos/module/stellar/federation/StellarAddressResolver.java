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
package org.mifos.module.stellar.federation;

import org.mifos.module.stellar.federation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves the address either using the installation local federation server or via the domain
 * name using the external federation service.
 */
@Component
public class StellarAddressResolver {
  private final LocalFederationService localfederationService;
  private final ExternalFederationService externalFederationService;

  @Autowired
  public StellarAddressResolver(
      final LocalFederationService localfederationService,
      final ExternalFederationService externalFederationService) {
    this.localfederationService = localfederationService;
    this.externalFederationService = externalFederationService;
  }

  public StellarAccountId getAccountIdOfStellarAccount(final StellarAddress stellarAddress)
      throws FederationFailedException
  {
    if (localfederationService.handlesDomain(stellarAddress.getDomain())) {
      return localfederationService.getAccountId(stellarAddress);
    }
    else {
      return externalFederationService.getAccountId(stellarAddress);
    }
  }
}
