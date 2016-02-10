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

import org.springframework.stereotype.Service;
import org.stellar.sdk.federation.*;
import org.stellar.sdk.federation.FederationResponse;

import java.io.IOException;

@Service
public class ExternalFederationService {

  class StellarResolver
  { //To make a static function mockable.
    public FederationResponse resolve(final String address) throws IOException {
      return Federation.resolve(address);
    }
  }

  private StellarResolver stellarResolver;

  ExternalFederationService()
  {
    this.stellarResolver = new StellarResolver();
  }

  ExternalFederationService(final StellarResolver stellarResolver)
  {
    this.stellarResolver = stellarResolver;
  }

  /**
   * Based on the stellar address, finds the stellar account id.  Resolves the domain, and calls
   * the federation service to do so.  This only returns an account id if the memo type is id or
   * there is no memo type.
   *
   * @param stellarAddress The stellar address for which to return a stellar account id.
   * @return The corresponding stellar account id.
   *
   * @throws FederationFailedException for the following cases:
   * * domain server not reachable,
   * * stellar.toml not parseable for federation server,
   * * federation server not reachable,
   * * federation server response does not match expected format.
   * * memo type is not id.
   */
  public StellarAccountId getAccountId(final StellarAddress stellarAddress)
      throws FederationFailedException
  {
    final org.stellar.sdk.federation.FederationResponse federationResponse;
    try {
      federationResponse = stellarResolver.resolve(stellarAddress.toString());
    }
    catch (final IOException e)
    {
      throw FederationFailedException
          .domainDoesNotReferToValidFederationServer(stellarAddress.getDomain().toString());
    }

    if (federationResponse == null)
    {
      throw FederationFailedException.addressNameNotFound(stellarAddress.toString());
    }
    if (federationResponse.getAccountId() == null)
    {
      throw FederationFailedException.addressNameNotFound(stellarAddress.toString());
    }

    return convertFederationResponseToStellarAddress(federationResponse);
  }

  private StellarAccountId convertFederationResponseToStellarAddress(
      final org.stellar.sdk.federation.FederationResponse response)
  {
    if (response.getMemoType().equalsIgnoreCase("text"))
    {
      return StellarAccountId.subAccount(response.getAccountId(), response.getMemo());
    }
    else if (response.getMemoType() == null || response.getMemoType().isEmpty())
    {
      return StellarAccountId.mainAccount(response.getAccountId());
    }
    else
    {
      throw FederationFailedException.addressRequiresUnsupportedMemoType(response.getMemoType());
    }
  }
}
