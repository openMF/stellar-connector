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

public class PassiveOfferData {

  private String buyingAssetCode;
  private String buyingIssuerAddress;
  private String sellingAssetCode;
  private String sellingIssuerAddress;
  private BigDecimal maximumAmount;
  private BigDecimal price;

  @SuppressWarnings("unused") //needed for Json Mapping.
  PassiveOfferData() {super();}

  public PassiveOfferData(
      final String buyingAssetCode, final String buyingIssuerAddress,
      final String sellingAssetCode, final String sellingIssuerAddress,
      final BigDecimal maximumAmount, final BigDecimal price) {
    this.buyingAssetCode = buyingAssetCode;
    this.buyingIssuerAddress = buyingIssuerAddress;
    this.sellingAssetCode = sellingAssetCode;
    this.sellingIssuerAddress = sellingIssuerAddress;
    this.maximumAmount = maximumAmount;
    this.price = price;
  }
}
