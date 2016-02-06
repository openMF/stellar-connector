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
package org.mifos.module.stellar.federation;

import com.google.common.net.InternetDomainName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class StellarAddressTest {

  private static class TestCase
  {
    private String inputedStringAddress = null;
    private String expectedTenantName = null;
    private Optional<String> expectedUserAccountId = Optional.empty();
    private InternetDomainName expectedDomain = null;
    private boolean expectedVaultAddress = false;
    private boolean exceptionExpected = false;

    TestCase inputedStringAddress(final String value)
    {
      inputedStringAddress = value;
      return this;
    }

    TestCase expectedTenantName(final String value)
    {
      expectedTenantName = value;
      return this;
    }

    TestCase expectedUserAccountId(final String value)
    {
      expectedUserAccountId = Optional.of(value);
      return this;
    }

    TestCase expectedDomain(final String value)
    {
      expectedDomain = InternetDomainName.from(value);
      return this;
    }

    public TestCase expectedVaultAddress() {
      expectedVaultAddress = true;
      return this;
    }

    TestCase exceptionExpected(final boolean value)
    {
      exceptionExpected = value;
      return this;
    }

    String getInputedStringAddress() {
      return inputedStringAddress;
    }

    InternetDomainName getExpectedDomain() {
      return expectedDomain;
    }

    String getExpectedTenantName() {
      return expectedTenantName;
    }

    Optional<String> getExpectedUserAccountId() {
      return expectedUserAccountId;
    }

    boolean getExpectedVaultAddress() { return expectedVaultAddress; }

    boolean getExceptionExpected() {
      return exceptionExpected;
    }

    public String toString()
    {
      return inputedStringAddress;
    }
  }

  private final TestCase testCase;

  public StellarAddressTest(final TestCase testCase)
  {
    this.testCase = testCase;
  }


  @Parameterized.Parameters
  public static Collection testCases() {
    Collection<TestCase> ret = new ArrayList<>();
    ret.add(new TestCase()
            .inputedStringAddress("equid*equid.co")
            .expectedDomain("equid.co")
            .expectedTenantName("equid"));

    ret.add(new TestCase()
        .inputedStringAddress("mifos:nonexistant*mifos.org")
        .expectedDomain("mifos.org")
        .expectedTenantName("mifos")
        .expectedUserAccountId("nonexistant"));

    ret.add(new TestCase()
        .inputedStringAddress("brokenaddress@mifos.org")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress("string with spaces*mifos.org")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress("string*with*multiple*stars")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress("string:with:multiple:colons*stars.com")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress(":starts.with.a.colon*stars.com")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress("x*space . in . domain . name")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress("*startswithastar.com")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress(" startswithaspace*star.com")
        .exceptionExpected(true));

    ret.add(new TestCase()
        .inputedStringAddress("")
        .exceptionExpected(true));

    ret.add(new TestCase()
    .inputedStringAddress("x:vault*star.com")
        .expectedDomain("star.com")
        .expectedTenantName("x")
        .expectedVaultAddress());


    return ret;
  }

  @Test
  public void testStellarAddress()
  {
    try {
      final StellarAddress result = StellarAddress.parse(testCase.getInputedStringAddress());
      assertEquals(testCase.getExceptionExpected(), false);

      assertEquals(testCase.getExpectedDomain(), result.getDomain());
      assertEquals(testCase.getExpectedTenantName(), result.getTenantName());
      assertEquals(testCase.getExpectedUserAccountId(), result.getUserAccountId());
      assertEquals(testCase.getExpectedVaultAddress(), result.isVaultAddress());

      assertEquals(testCase.getInputedStringAddress(), result.toString());
    }
    catch (final InvalidStellarAddressException ex)
    {
      assertEquals(testCase.getExceptionExpected(), true);
    }

  }
}
