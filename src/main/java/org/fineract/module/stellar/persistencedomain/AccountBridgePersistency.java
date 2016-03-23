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
@Table(name = "stellar_account_bridge")
public class AccountBridgePersistency {
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "mifos_tenant_id")
  private String mifosTenantId;

  @Column(name = "mifos_token")
  private String mifosToken;

  @Column(name = "mifos_staging_account")
  private String mifosStagingAccount;

  @Column(name = "stellar_account_id")
  private String stellarAccountId;

  @Column(name = "stellar_account_private_key")
  private char[] stellarAccountPrivateKey;

  @Column(name = "stellar_vault_account_id")
  private String stellarVaultAccountId;

  @Column(name = "stellar_vault_account_private_key")
  private char[] stellarVaultAccountPrivateKey;

  @Column(name = "endpoint")
  private String endpoint;

  @SuppressWarnings("unused")
  public AccountBridgePersistency() {}

  public AccountBridgePersistency(
      final String mifosTenantId,
      final String mifosToken,
      final String mifosStagingAccount,
      final String stellarAccountId,
      final char[] stellarAccountPrivateKey,
      final String endpoint) {
    this.mifosTenantId = mifosTenantId;
    this.mifosToken = mifosToken;
    this.mifosStagingAccount = mifosStagingAccount;
    this.stellarAccountId = stellarAccountId;
    this.stellarAccountPrivateKey = stellarAccountPrivateKey;
    stellarVaultAccountId = null;
    stellarVaultAccountPrivateKey = null;
    this.endpoint = endpoint;
  }

  public void setStellarVaultAccountId(final String stellarVaultAccountId) {
    this.stellarVaultAccountId = stellarVaultAccountId;
  }

  public void setStellarVaultAccountPrivateKey(final char[] stellarVaultAccountPrivateKey) {
    this.stellarVaultAccountPrivateKey = stellarVaultAccountPrivateKey;
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

  public String getMifosStagingAccount() {
    return mifosStagingAccount;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getStellarAccountId() {
    return stellarAccountId;
  }

  public char[] getStellarAccountPrivateKey() {
    return stellarAccountPrivateKey;
  }

  public String getStellarVaultAccountId() { return stellarVaultAccountId; }

  public char[] getStellarVaultAccountPrivateKey() {
    return stellarVaultAccountPrivateKey;
  }
}
