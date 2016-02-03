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

import java.math.BigDecimal;

public class TrustLineConfiguration {

  private String trustedStellarAddress;

  private String trustedAssetCode;

  private BigDecimal maximumAmount;

  @SuppressWarnings("unused") //needed for Json Mapping.
  TrustLineConfiguration() {super();}

  public TrustLineConfiguration(
      final String trustedStellarAddress,
      final String trustedAssetCode,
      final BigDecimal maximumAmount) {
    this.trustedStellarAddress = trustedStellarAddress;
    this.trustedAssetCode = trustedAssetCode;
    this.maximumAmount = maximumAmount;
  }

  public String getTrustedStellarAddress() {
    return trustedStellarAddress;
  }

  public String getTrustedAssetCode() {
    return trustedAssetCode;
  }

  public BigDecimal getMaximumAmount() {
    return maximumAmount;
  }
}
