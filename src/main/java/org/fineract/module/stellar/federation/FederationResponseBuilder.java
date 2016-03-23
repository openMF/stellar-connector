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

import org.stellar.sdk.federation.FederationResponse;

/**
 * Federation response as documented in
 * https://www.stellar.org/developers/learn/concepts/federation.html
 */
public class FederationResponseBuilder {
  public static FederationResponse accountInMemoField(
      final String stellarAddress,
      final String accountId,
      final String userAccount)
  {
    return new FederationResponse(stellarAddress, accountId, "text", userAccount);
  }

  public static FederationResponse account(
      final String stellarAddress,
      final String accountId)
  {
    return new FederationResponse(stellarAddress, accountId, "", "");
  }
}
