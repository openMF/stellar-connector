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
import java.util.Date;

@Entity
@Table(name = "stellar_cursor")
public class StellarCursorPersistency {

  @SuppressWarnings("unused")
  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "cursor")
  private String cursor;

  @SuppressWarnings("unused")
  @Column(name = "processed")
  private Boolean processed;

  @Column(name = "created_on")
  @Temporal(TemporalType.TIMESTAMP)
  private Date createdOn;

  @SuppressWarnings("unused")
  public StellarCursorPersistency() { }

  public StellarCursorPersistency(final String cursor, final Date createdOn) {
    this.cursor = cursor;
    this.processed = false;
    this.createdOn = createdOn;
  }

  public String getCursor() {
    return cursor;
  }

  public void setProcessed(Boolean processed) {
    this.processed = processed;
  }

  @SuppressWarnings("unused")
  public Date getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(final Date createdOn) {
    this.createdOn = createdOn;
  }
}
