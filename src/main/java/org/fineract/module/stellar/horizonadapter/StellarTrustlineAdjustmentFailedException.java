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
package org.fineract.module.stellar.horizonadapter;

public class StellarTrustlineAdjustmentFailedException extends RuntimeException {
  private StellarTrustlineAdjustmentFailedException(final String message) {
    super(message);
  }

  public static StellarTrustlineAdjustmentFailedException needTopLevelStellarAccount
      (final String address) {
    return new StellarTrustlineAdjustmentFailedException(
        "Need top level Stellar account to create trustline: " + address);
  }

  public static StellarTrustlineAdjustmentFailedException trustLineTransactionFailed() {
    return new StellarTrustlineAdjustmentFailedException("Credit line creation failed.");
  }

  public static StellarTrustlineAdjustmentFailedException selfReferentialVaultTrustline
      (final String stellarAddress)
  {
    return new StellarTrustlineAdjustmentFailedException(
        "Trustline to accounts vault should be managed via vault: " + stellarAddress);
  }
}
