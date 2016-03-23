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

public class SecurityException extends RuntimeException {
  private SecurityException(final String msg) {
    super(msg);
  }

  public static SecurityException invalidApiKey(@SuppressWarnings("unused") final String apiKey)
  {
    return new SecurityException("The api key is invalid");
  }

  public static SecurityException apiKeyAlreadyExistsForTenant(
      @SuppressWarnings("unused")final String tenant)
  {
    return new SecurityException("authorization failed.");
    //Don't say more -- the caller has no rights to know who already has a bridge.
  }

  public static SecurityException invalidTenantId(
      @SuppressWarnings("unused") final String mifosTenantId) {
    return new SecurityException("authorization failed.");
    //Again: don't say more.
  }
}
