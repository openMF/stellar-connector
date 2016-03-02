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

public class InvalidStellarAddressException extends RuntimeException {
  private InvalidStellarAddressException(final String message) {
    super(message);
  }

  public static InvalidStellarAddressException invalidDomainName(final String domainName) {
    return new InvalidStellarAddressException("Domain name is not valid: " + domainName);
  }

  public static InvalidStellarAddressException nonConformantStellarAddress(final String address) {
    return new InvalidStellarAddressException(
        "Non-conformant stellar address: " + address
    );
  }
}
