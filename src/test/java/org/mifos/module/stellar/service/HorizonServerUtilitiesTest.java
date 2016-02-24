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
package org.mifos.module.stellar.service;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;

public class HorizonServerUtilitiesTest {
  @Test
  public void determineOfferAmount()
  {
    final BigDecimal offerAmount = HorizonServerUtilities
        .determineOfferAmount(BigDecimal.valueOf(20), BigDecimal.valueOf(10),
            BigDecimal.valueOf(15));
    Assert.assertTrue(offerAmount.compareTo(BigDecimal.TEN) == 0);
  }
}
