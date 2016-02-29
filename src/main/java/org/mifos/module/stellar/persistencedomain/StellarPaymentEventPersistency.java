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
import java.util.Date;

@Entity
@Table(name = "stellar_payment_event")
public class StellarPaymentEventPersistency {
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "mifos_tenant_id")
  private String mifosTenantId;

  @Column(name = "asset_code")
  private String assetCode;

  @Column(name = "amount")
  private String amount;

  @Column(name = "processed")
  private Boolean processed;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_on")
  @Temporal(TemporalType.TIMESTAMP)
  private Date createdOn;

  @SuppressWarnings("unused")
  public StellarPaymentEventPersistency() {}

  public Long getId() {
    return id;
  }

  public void setId(final Long id) {
    this.id = id;
  }

  public String getMifosTenantId() {
    return mifosTenantId;
  }

  public void setMifosTenantId(final String mifosTenantId) {
    this.mifosTenantId = mifosTenantId;
  }

  public String getAssetCode() {
    return assetCode;
  }

  public void setAssetCode(String assetCode) {
    this.assetCode = assetCode;
  }

  public String getAmount() {
    return amount;
  }

  public void setAmount(String amount) {
    this.amount = amount;
  }

  public Boolean getProcessed() {
    return processed;
  }

  public void setProcessed(final Boolean processed) {
    this.processed = processed;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Date getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(Date createdOn) {
    this.createdOn = createdOn;
  }
}
