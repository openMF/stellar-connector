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

public class FederationFailedException extends RuntimeException {
  private FederationFailedException(final String message) { super(message);
  }

  public static FederationFailedException domainDoesNotReferToValidFederationServer
      (final String domain)
  {
    return new FederationFailedException(
        "The federation server for the given domain could not be reached: " + domain);
  }

  public static FederationFailedException addressRequiresUnsupportedMemoType(final String memoType)
  {
    return new FederationFailedException(
        "The given federation address returned an unsupported memo type: " + memoType);
  }

  public static FederationFailedException wrongDomain(final String domain) {
    return new FederationFailedException("Wrong domain: " + domain);
  }

  public static FederationFailedException addressNameNotFound(final String address) {
    return new FederationFailedException("The address name is not found: " + address);
  }

  public static FederationFailedException malformedAddress(final String address) {
    return new FederationFailedException("The address is not a valid stellar address: " + address);
  }

  public static FederationFailedException needTopLevelStellarAccount
      (final String address) {
    return new FederationFailedException(
        "Need top level Stellar account: " + address);
  }
}
