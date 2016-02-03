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

@Entity
@Table(name = "stellar_account_bridge")
public class AccountBridgePersistency {
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "mifos_tenant_id")
  private String mifosTenantId;

  @Column(name = "mifos_token")
  private String mifosToken;

  @Column(name = "stellar_account_id")
  private String stellarAccountId;

  @Column(name = "stellar_account_private_key")
  private char[] stellarAccountPrivateKey;

  @SuppressWarnings("unused")
  public AccountBridgePersistency() {}

  public AccountBridgePersistency(
      final String mifosTenantId,
      final String mifosToken,
      final String stellarAccountId,
      final char[] stellarAccountPrivateKey) {
    this.mifosTenantId = mifosTenantId;
    this.mifosToken = mifosToken;
    this.stellarAccountId = stellarAccountId;
    this.stellarAccountPrivateKey = stellarAccountPrivateKey;
  }

  public Long getId() {
    return id;
  }

  public String getMifosTenantId() {
    return mifosTenantId;
  }

  public String getMifosToken() {
    return mifosToken;
  }

  public String getStellarAccountId() {
    return stellarAccountId;
  }

  public char[] getStellarAccountPrivateKey() {
    return stellarAccountPrivateKey;
  }
}
