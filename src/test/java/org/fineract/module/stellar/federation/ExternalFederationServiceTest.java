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
package org.fineract.module.stellar.federation;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.stellar.sdk.federation.MalformedAddressException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class ExternalFederationServiceTest {
  enum AddressDomainAccessBehaviorEnum
  {
    CONNECTION_FAILS,
    ADDRESS_IS_MALFORMED,
    EXTERNAL_FEDERATION_RETURNS_NULL,
    SUCCESS
  }

  private static class TestCase {
    private String inputtedStellarAddress = null;

    public AddressDomainAccessBehaviorEnum addressDomainAccessBehavior =
        AddressDomainAccessBehaviorEnum.SUCCESS;

    private String outputtedMemoType = "";
    private String outputtedMemo = "";

    private String expectedPublicKey = null;
    private Optional<String> expectedSubAccount = Optional.empty();

    private boolean exceptionExpected = false;

    TestCase inputtedStellarAddress(final String value)
    {
      this.inputtedStellarAddress = value;
      return this;
    }

    TestCase addressDomainAccessBehavior(final AddressDomainAccessBehaviorEnum newVal) {
      this.addressDomainAccessBehavior = newVal;
      return this;
    }

    TestCase outputtedMemoType(final String value)
    {
      this.outputtedMemoType = value;
      return this;
    }

    TestCase outputtedMemo(final String value)
    {
      this.outputtedMemo = value;
      return this;
    }

    TestCase expectedPublicKey(final String value)
    {
      this.expectedPublicKey = value;
      return this;
    }

    TestCase expectedSubAccount(final String value)
    {
      this.expectedSubAccount = Optional.of(value);
      return this;
    }

    TestCase exceptionExpected(final boolean value) {
      this.exceptionExpected = value;
      return this;
    }
  }

  private final TestCase testCase;

  public ExternalFederationServiceTest(final TestCase testCase)
  {
    this.testCase = testCase;
  }


  @Parameterized.Parameters
  public static Collection testCases() {
    final Collection<TestCase> ret = new ArrayList<>();
    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .expectedPublicKey("G12351354")
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .expectedPublicKey("G12351354")
        .outputtedMemoType("text")
        .outputtedMemo("xyz")
        .expectedSubAccount("xyz")
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .expectedPublicKey("G12351354")
        .outputtedMemoType("hash") //Only text type is currently supported.
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .expectedPublicKey("G12351354")
        .outputtedMemoType("id") //Only text type is currently supported.
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainAccessBehavior(AddressDomainAccessBehaviorEnum.CONNECTION_FAILS)
        //Can happen if there's nothing at that URL (for example).
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainAccessBehavior(AddressDomainAccessBehaviorEnum.ADDRESS_IS_MALFORMED)
        //Can happen if there's nothing at that URL (for example).
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .expectedPublicKey(null)
        //Can happen if there's nothing at that URL (for example).
        .exceptionExpected(true)
    );


    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainAccessBehavior(
            AddressDomainAccessBehaviorEnum.EXTERNAL_FEDERATION_RETURNS_NULL)
        //Can happen if there's nothing at that URL (for example).
        .exceptionExpected(true)
    );

    return ret;
  }

  @Test
  public void testGetAccountId() {
    checkTestCaseConsistency();
    //(Not strictly necessary, but helpful for avoiding test case programming errors.)

    final ExternalFederationService.StellarResolver externalCallsMock =
        getExternalCallsMock();

    try {
      final ExternalFederationService testSubject =
          new ExternalFederationService(externalCallsMock);
      final StellarAccountId stellarAccountId =
          testSubject.getAccountId(StellarAddress.parse(testCase.inputtedStellarAddress));
      assertEquals(testCase.exceptionExpected, false);

      assertEquals(testCase.expectedPublicKey, stellarAccountId.getPublicKey());
      assertEquals(testCase.expectedSubAccount, stellarAccountId.getSubAccount());
    }
    catch (final FederationFailedException e)
    {
      assertEquals(testCase.exceptionExpected, true);
    }
  }

  private void checkTestCaseConsistency() {
    final String INCOMPLETE = "Testcase initialization incomplete";
    assertFalse(INCOMPLETE, testCase.inputtedStellarAddress == null);
    if (testCase.addressDomainAccessBehavior != AddressDomainAccessBehaviorEnum.SUCCESS
        && !testCase.exceptionExpected) {
      assertFalse(INCOMPLETE, testCase.expectedPublicKey == null);
    }
  }

  private ExternalFederationService.StellarResolver getExternalCallsMock() {
    final Mockery context = new Mockery();
    context.setImposteriser(ClassImposteriser.INSTANCE);


    final ExternalFederationService.StellarResolver externalCallsMock =
        context.mock(ExternalFederationService.StellarResolver.class);

    context.checking( new Expectations() {{

      try {
        allowing(externalCallsMock).resolve(testCase.inputtedStellarAddress);
      } catch (IOException e1) {
        assertFalse("An IOException should never be thrown here in the test.", true);
      }
      switch (testCase.addressDomainAccessBehavior) {
        case SUCCESS:
          will(returnValue(
              new org.stellar.sdk.federation.FederationResponse(
                  testCase.inputtedStellarAddress, testCase.expectedPublicKey,
                  testCase.outputtedMemoType, testCase.outputtedMemo)));
          break;
        case CONNECTION_FAILS:
          will(throwException(new IOException()));
          break;
        case ADDRESS_IS_MALFORMED:
          will(throwException(new MalformedAddressException()));
          break;
        case EXTERNAL_FEDERATION_RETURNS_NULL:
          will(returnValue(null));
        default:
          break;
      }
    }});
    return externalCallsMock;
  }
}
