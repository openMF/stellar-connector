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
package org.fineract.module.stellar.persistencedomain;


import javax.persistence.*;

@Entity
@Table(name = "stellar_account_bridge_key")
public class AccountBridgeKeyPersistency {
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "rest_api_key")
  private String restApiKey;

  @Column(name = "mifos_tenant_id")
  private String mifosTenantId;

  @SuppressWarnings("unused")
  public AccountBridgeKeyPersistency() {}

  public AccountBridgeKeyPersistency(
      final String restApiKey,
      final String mifosTenantId)

  {

    this.restApiKey = restApiKey;
    this.mifosTenantId = mifosTenantId;
  }

  public Long getId() {
    return id;
  }

  public String getRestApiKey() {
    return restApiKey;
  }

  public String getMifosTenantId() {
    return mifosTenantId;
  }
}
