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
package org.mifos.module.stellar;

import org.hamcrest.Description;

import java.math.BigDecimal;

public class AccountBalanceMatcher extends org.hamcrest.BaseMatcher<String> {
  private final BigDecimal expectedAmount;

  public static AccountBalanceMatcher balanceMatches(final BigDecimal amount)
  {
    return new AccountBalanceMatcher(amount);
  }

  private AccountBalanceMatcher(final BigDecimal amount)
  {
    this.expectedAmount = amount;
  }

  @Override public boolean matches(final Object item) {
    if (!(item instanceof String))
    {
      return false;
    }

    final BigDecimal actualAmount = BigDecimal.valueOf(Double.parseDouble((String) item));

    return actualAmount.compareTo(expectedAmount) == 0;
  }

  @Override public void describeTo(final Description description) {
    description.appendValue(expectedAmount);
  }
}
