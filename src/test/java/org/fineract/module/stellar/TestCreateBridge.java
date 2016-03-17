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
package org.fineract.module.stellar;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import org.fineract.module.stellar.configuration.BridgeConfiguration;
import org.fineract.module.stellar.repository.OrphanedStellarAccountRepository;
import org.fineract.module.stellar.restdomain.AccountBridgeConfiguration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static com.jayway.restassured.RestAssured.given;
import static org.fineract.module.stellar.StellarBridgeTestHelpers.TEST_ADDRESS_DOMAIN;
import static org.fineract.module.stellar.StellarBridgeTestHelpers.createBridge;

@Component
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(BridgeConfiguration.class)
@WebIntegrationTest({
    "server.port=0", //randomPort = true
    "stellar.installation-account-private-key="
        + StellarDocker.MASTER_ACCOUNT_PRIVATE_KEY,
    "stellar.new-account-initial-balance=1020",
    "stellar.local-federation-domain=" + TEST_ADDRESS_DOMAIN
})
public class TestCreateBridge {
  static FineractStellarTestRig testRig;

  @Autowired OrphanedStellarAccountRepository orphanedStellarAccountRepository;

  @BeforeClass
  public static void setupSystem() throws Exception {
    testRig = new FineractStellarTestRig();
  }

  @AfterClass
  public static void tearDownSystem() throws Exception {
    testRig.close();
  }

  @Value("${local.server.port}")
  int bridgePort;

  @Before
  public void setupTest() {
    RestAssured.port = bridgePort;

  }

  @Test
  public void createBridgeWithInvalidStellarAddressMifosTenant()
  {
    String firstTenantId = "invalid*stellar*address";
    final String mifosAddress = testRig.getMifosAddress();

    final AccountBridgeConfiguration newAccount =
        new AccountBridgeConfiguration(
            firstTenantId, StellarBridgeTestHelpers.getTenantToken(firstTenantId, mifosAddress), mifosAddress);
    final Response creationResponse =
        given()
            .header(StellarBridgeTestHelpers.CONTENT_TYPE_HEADER)
            .body(newAccount)
            .post("/modules/stellarbridge");

    creationResponse
        .then().assertThat().statusCode(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  public void deleteBridgeDoesntOrphanAccounts()
  {
    final int previousOrphanCount = orphanCount();

    final String apiKey = createBridge("default", testRig.getMifosAddress());

    StellarBridgeTestHelpers.deleteBridge("default", apiKey);


    final int newOrphanCount = orphanCount() - previousOrphanCount;

    Assert.assertEquals("new orphans should be zero.", 0, newOrphanCount);
  }

  private int orphanCount() {
    return orphanedStellarAccountRepository.findByMifosTenantId("default").size();
  }
}
