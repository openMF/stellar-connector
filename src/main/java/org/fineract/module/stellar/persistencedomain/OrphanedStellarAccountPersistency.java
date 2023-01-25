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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;


@Entity
@Table(name = "orphaned_stellar_account")
public class OrphanedStellarAccountPersistency {
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "mifos_tenant_id")
  private String mifosTenantId;

  @Column(name = "stellar_account_id")
  private String stellarAccountId;

  @Column(name = "stellar_account_private_key")
  private char[] stellarAccountPrivateKey;

  @Column(name = "reason_removal_failed")
  private String reasonRemovalFailed;

  @Column(name = "last_cursor")
  private String lastCursor;

  @Column(name = "vault_account")
  private Boolean vaultAccount;

  @SuppressWarnings("unused")
  public OrphanedStellarAccountPersistency() {}

  public OrphanedStellarAccountPersistency(
      final String mifosTenantId,
      final String stellarAccountId,
      final char[] stellarAccountPrivateKey,
      final String reasonRemovalFailed,
      final String lastCursor,
      final Boolean vaultAccount) {
    this.mifosTenantId = mifosTenantId;
    this.stellarAccountId = stellarAccountId;
    this.stellarAccountPrivateKey = stellarAccountPrivateKey;
    this.reasonRemovalFailed = reasonRemovalFailed;
    this.lastCursor = lastCursor;
    this.vaultAccount = vaultAccount;
  }

  @Override public String toString() {
    return "OrphanedStellarAccountPersistency{" +
        "mifosTenantId='" + mifosTenantId + '\'' +
        ", stellarAccountId='" + stellarAccountId + '\'' +
        ", reasonRemovalFailed='" + reasonRemovalFailed + '\'' +
        ", vaultAccount=" + vaultAccount +
        '}';
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getMifosTenantId() {
    return mifosTenantId;
  }

  public void setMifosTenantId(String mifosTenantId) {
    this.mifosTenantId = mifosTenantId;
  }

  @SuppressWarnings("unused")
  public String getStellarAccountId() {
    return stellarAccountId;
  }

  @SuppressWarnings("unused")
  public void setStellarAccountId(String stellarAccountId) {
    this.stellarAccountId = stellarAccountId;
  }

  @SuppressWarnings("unused")
  public char[] getStellarAccountPrivateKey() {
    return stellarAccountPrivateKey;
  }

  @SuppressWarnings("unused")
  public void setStellarAccountPrivateKey(char[] stellarAccountPrivateKey) {
    this.stellarAccountPrivateKey = stellarAccountPrivateKey;
  }

  @SuppressWarnings("unused")
  public String getReasonRemovalFailed() {
    return reasonRemovalFailed;
  }

  @SuppressWarnings("unused")
  public void setReasonRemovalFailed(String reasonRemovalFailed) {
    this.reasonRemovalFailed = reasonRemovalFailed;
  }

  @SuppressWarnings("unused")
  public String getLastCursor() {
    return lastCursor;
  }

  @SuppressWarnings("unused")
  public void setLastCursor(String lastCursor) {
    this.lastCursor = lastCursor;
  }

  @SuppressWarnings("unused")
  public Boolean getVaultAccount() {
    return vaultAccount;
  }

  @SuppressWarnings("unused")
  public void setVaultAccount(Boolean vaultAccount) {
    this.vaultAccount = vaultAccount;
  }
}
