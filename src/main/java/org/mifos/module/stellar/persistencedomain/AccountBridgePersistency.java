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
package org.mifos.module.stellar.persistencedomain;

import javax.persistence.*;
import java.util.Arrays;

@Entity
@Table(name = "stellar_account_bridge")
public class AccountBridgePersistency {
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "rest_api_key")
  private final String restApiKey;

  @Column(name = "mifos_tenant_id")
  private final String mifosTenantId;

  @Column(name = "mifos_token")
  private final String mifosToken;

  @Column(name = "stellar_account_public_key")
  private final String stellarAccountPublicKey;

  @Column(name = "stellar_account_private_key")
  private final byte[] stellarAccountPrivateKey;

  public AccountBridgePersistency(
      final String restApiKey,
      final String mifosTenantId,
      final String mifosToken,
      final String stellarAccountPublicKey,
      final byte[] stellarAccountPrivateKey) {

    this.restApiKey = restApiKey;
    this.mifosTenantId = mifosTenantId;
    this.mifosToken = mifosToken;
    this.stellarAccountPublicKey = stellarAccountPublicKey;
    this.stellarAccountPrivateKey = stellarAccountPrivateKey;
  }

  public void clearSensitiveData()
  {
    Arrays.fill(stellarAccountPrivateKey, (byte)0);
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

  public String getMifosToken() {
    return mifosToken;
  }

  public String getStellarAccountPublicKey() {
    return stellarAccountPublicKey;
  }

  public byte[] getStellarAccountPrivateKey() {
    return stellarAccountPrivateKey;
  }
}
