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

public class AccountMergerFailedException extends RuntimeException {
  private AccountMergerFailedException(final String msg) {super(msg);}

  public static AccountMergerFailedException accountIsNotEmpty(final String stellarAccountId) {
    return new AccountMergerFailedException("Stellar account: " + stellarAccountId +
    " is not empty and could not be removed.");
  }

  public static AccountMergerFailedException stellarRefused() {
    return new AccountMergerFailedException("Stellar account could not be removed.");
  }

  public static AccountMergerFailedException vaultIssuedAssetsAreStillInCirculation() {
    return new AccountMergerFailedException("Stellar vault account could not be removed, because vault issued assets are still in circulation.");
  }
}
