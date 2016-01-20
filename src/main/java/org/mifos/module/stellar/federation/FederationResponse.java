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

import com.google.gson.annotations.SerializedName;

/**
 * Federation response as documented in
 * https://www.stellar.org/developers/learn/concepts/federation.html
 */
public class FederationResponse {
  @SerializedName("stellar_address")
  private String stellarAddress; // <username*domain.tld>,

  @SerializedName("account_id")
  private String accountId; // <account_id>,

  @SerializedName("memo_type")
  private String memoType; // <"text", "id" , or "hash"> *optional*

  @SerializedName("memo")
  private String memo; //<memo to attach to any payment. if "hash" type then will be base32 encoded> *optional*


  public static FederationResponse invalidType(final String type) {
    return new FederationResponse(null, null, null, "queried type is invalid: " + type);
  }

  public static FederationResponse accountInMemoField(
      final String stellarAddress,
      final String accountId,
      final String userAccount)
  {
    return new FederationResponse(stellarAddress, accountId, "id", userAccount);
  }

  public static FederationResponse account(
      final String stellarAddress,
      final String accountId)
  {
    return new FederationResponse(stellarAddress, accountId, "", "");
  }

  FederationResponse(
      final String stellarAddress,
      final String accountId,
      final String memoType,
      final String memo)
  {
    this.stellarAddress = stellarAddress;
    this.accountId = accountId;
    this.memoType = memoType;
    this.memo = memo;
  }

  public String getMemo() {
    return memo;
  }

  @SuppressWarnings("unused")
  public String getStellarAddress() { return stellarAddress; }

  public String getAccountId() {
    return accountId;
  }

  public String getMemoType() {
    return memoType;
  }
}
