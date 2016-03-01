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
package org.mifos.module.stellar.restdomain;

public class AccountBridgeConfiguration {

  private String mifosTenantId;

  private String mifosToken;

  private String endpoint;

  @SuppressWarnings("unused") //needed for Json Mapping.
  AccountBridgeConfiguration() {super();}

  public AccountBridgeConfiguration(
      final String mifosTenantId,
      final String mifosToken,
      final String endpoint)
  {
    this.mifosTenantId = mifosTenantId;
    this.mifosToken = mifosToken;
    this.endpoint = endpoint;
  }

  public String getMifosTenantId() {
    return mifosTenantId;
  }

  public String getMifosToken() {
    return mifosToken;
  }

  public String getEndpoint() { return endpoint; }
}
