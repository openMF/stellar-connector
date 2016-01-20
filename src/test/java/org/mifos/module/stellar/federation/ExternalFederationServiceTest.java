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

import com.moandjiezana.toml.Toml;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class ExternalFederationServiceTest {
  private static class TestCase {
    private StellarAddress inputtedStellarAddress = null;

    private URL addressDomainUrl = null;
    public boolean addressDomainAccessFails = false;
    private String federationServerApi = null;

    private String outputtedMemoType = "";
    private String outputtedMemo = "";
    private HttpStatus outputtedStatus = HttpStatus.OK;

    private String expectedPublicKey = null;
    private Optional<String> expectedSubAccount = Optional.empty();

    private boolean exceptionExpected = false;

    TestCase inputtedStellarAddress(final String value)
    {
      this.inputtedStellarAddress = StellarAddress.parse(value);
      return this;
    }

    TestCase addressDomainUrl(final String value) {
      try {
        this.addressDomainUrl = new URL(value);
      } catch (MalformedURLException e) {
        assertFalse("This exception should never occur here.", true);
      }
      return this;
    }

    TestCase addressDomainAccessFails(final boolean value) {
      this.addressDomainAccessFails = value;
      return this;
    }

    TestCase federationServerApi(final String value) {
      this.federationServerApi = value;
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

    TestCase outputtedStatus(final HttpStatus value)
    {
      this.outputtedStatus = value;
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
        .addressDomainUrl("https://x.org/.well-known/stellar.toml")
        .federationServerApi("https://api.x.org/federation")
        .expectedPublicKey("G12351354")
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainUrl("https://x.org/.well-known/stellar.toml")
        .federationServerApi("https://api.x.org/federation")
        .expectedPublicKey("G12351354")
        .outputtedMemoType("id")
        .outputtedMemo("xyz")
        .expectedSubAccount("xyz")
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainUrl("https://x.org/.well-known/stellar.toml")
        .federationServerApi("https://api.x.org/federation")
        .expectedPublicKey("G12351354")
        .outputtedMemoType("hash") //Only id type is currently supported.
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainUrl("https://x.org/.well-known/stellar.toml")
        .federationServerApi("https://api.x.org/federation")
        .outputtedStatus(HttpStatus.NOT_FOUND) //Can happen if the federation server is not running.
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainUrl("https://x.org/.well-known/stellar.toml")
        .addressDomainAccessFails(true) //Can happen if there's nothing at that URL (for example).
        .exceptionExpected(true)
    );

    ret.add(new TestCase()
        .inputtedStellarAddress("x*x.org")
        .addressDomainUrl("https://x.org/.well-known/stellar.toml")
        .federationServerApi(null) //Can happen if the necessary TOML property is not present.
        .exceptionExpected(true)
    );

    return ret;
  }

  @Test
  public void testGetAccountId() {
    checkTestCaseConsistency();
    //(Not strictly necessary, but helpful for avoiding test case programming errors.)

    final ExternalFederationService.MockableExternalCalls externalCallsMock =
        getExternalCallsMock();

    try {
      final ExternalFederationService testSubject =
          new ExternalFederationService(externalCallsMock);
      final StellarAccountId stellarAccountId =
          testSubject.getAccountId(testCase.inputtedStellarAddress);
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
    assertFalse(INCOMPLETE, testCase.addressDomainUrl == null);
    if (!testCase.addressDomainAccessFails && !testCase.exceptionExpected) {
      assertFalse(INCOMPLETE, testCase.federationServerApi == null);
      assertFalse(INCOMPLETE, testCase.expectedPublicKey == null);
    }
  }

  private ExternalFederationService.MockableExternalCalls getExternalCallsMock() {
    final Mockery context = new Mockery();
    context.setImposteriser(ClassImposteriser.INSTANCE);


    final ExternalFederationService.MockableExternalCalls externalCallsMock =
        context.mock(ExternalFederationService.MockableExternalCalls.class);
    final ExternalFederationService.FederationService federationServiceMock =
        context.mock(ExternalFederationService.FederationService.class);
    final InputStream tomlStreamMock = context.mock(InputStream.class);
    final Toml tomlParserMock = context.mock(Toml.class);

    context.checking( new Expectations() {{

      try {
        allowing(externalCallsMock).getStreamFromUrl(testCase.addressDomainUrl);
      } catch (IOException e1) {
        assertFalse("An IOException should never be thrown here in the test.", true);
      }
      if (!testCase.addressDomainAccessFails) {
        will(returnValue(tomlStreamMock));
      }
      else {
        will(throwException(new IOException()));
      }

      allowing(externalCallsMock).getTomlParser(tomlStreamMock);
      will(returnValue(tomlParserMock));

      allowing(tomlParserMock).getString("FEDERATION_SERVER");
      will(returnValue(testCase.federationServerApi));

      allowing(externalCallsMock).getExternalFederationService(testCase.federationServerApi);
      will(returnValue(federationServiceMock));

      allowing(federationServiceMock).getAccountIdFromAddress(
          "name", testCase.inputtedStellarAddress.toString());
      if (testCase.outputtedStatus == HttpStatus.OK) {
        will(returnValue(
            new FederationResponse(
                testCase.inputtedStellarAddress.toString(), testCase.expectedPublicKey,
                testCase.outputtedMemoType, testCase.outputtedMemo)));
      }
      else {
        will(throwException(new ExternalFederationService.FederationApiGetFailed(
            testCase.outputtedStatus)));
      }

    }});
    return externalCallsMock;
  }
}
