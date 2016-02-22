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
@Table(name = "mifos_event")
public class MifosEventPersistency {

  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "payload")
  private String payload;

  @Column(name = "processed")
  private Boolean processed;

  @Column(name = "outstanding_retries")
  private Integer outstandingRetries;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_on")
  @Temporal(TemporalType.TIMESTAMP)
  private Date createdOn;

  @Column(name = "last_modified_on")
  @Temporal(TemporalType.TIMESTAMP)
  private Date lastModifiedOn;


  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(final String payload) {
    this.payload = payload;
  }

  @SuppressWarnings("unused")
  public Boolean getProcessed() {
    return processed;
  }

  public void setProcessed(final Boolean processed) {
    this.processed = processed;
  }

  public Integer getOutstandingRetries() {
    return outstandingRetries;
  }

  public void setOutstandingRetries(final Integer outstandingRetries) {
    this.outstandingRetries = outstandingRetries;
  }

  @SuppressWarnings("unused")
  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @SuppressWarnings("unused")
  public Date getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(final Date createdOn) {
    this.createdOn = createdOn;
  }

  @SuppressWarnings("unused")
  public Date getLastModifiedOn() {
    return lastModifiedOn;
  }

  public void setLastModifiedOn(final Date lastModifiedOn) {
    this.lastModifiedOn = lastModifiedOn;
  }
}
