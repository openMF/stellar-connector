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

public class StellarPaymentFailedException extends RuntimeException {
  public StellarPaymentFailedException(final String msg) {
    super(msg);
  }

  public static StellarPaymentFailedException noPathExists(final String assetCode) {
    return new StellarPaymentFailedException("No path exists in the given currency: " + assetCode);
  }

  public static StellarPaymentFailedException transactionFailed() {
    return new StellarPaymentFailedException(
        "Stellar Horizon server did not accept payment for unknown reason.");
  }
}
