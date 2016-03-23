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
import org.junit.*;
import org.junit.runner.RunWith;
import org.fineract.module.stellar.configuration.BridgeConfiguration;
import org.fineract.module.stellar.restdomain.TrustLineConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.*;
import static org.fineract.module.stellar.StellarBridgeTestHelpers.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(BridgeConfiguration.class)
@WebIntegrationTest({
    "server.port=0", //randomPort = true
    "stellar.installation-account-private-key="
        + StellarDocker.MASTER_ACCOUNT_PRIVATE_KEY,
    "stellar.new-account-initial-balance=1020",
    "stellar.local-federation-domain=" + TEST_ADDRESS_DOMAIN
})
public class TestCreateTrustLine {
  public static final String ASSET_CODE = "XXX";

  @Value("${local.server.port}")
  int bridgePort;


  static FineractStellarTestRig testRig;

  private Cleanup testCleanup = new Cleanup();
  private String firstTenantId;
  private String firstTenantApiKey;

  @BeforeClass
  public static void setupSystem() throws Exception {
    testRig = new FineractStellarTestRig();
  }

  @Before
  public void setupTest() {
    RestAssured.port = bridgePort;

    firstTenantId = "default";
    firstTenantApiKey = createAndDestroyBridge(firstTenantId, testCleanup, testRig.getMifosAddress());
  }

  @After
  public void tearDownTest() throws Exception {
    testCleanup.cleanup();
  }

  @AfterClass
  public static void tearDownSystem() throws Exception {
    testRig.close();
  }

  @Test
  public void createTrustLineIncorrectApiKey() {
    final TrustLineConfiguration trustLine =
        new TrustLineConfiguration(BigDecimal.valueOf(100));

    String issuer = "blub";

    given().header(CONTENT_TYPE_HEADER)
        .header(API_KEY_HEADER_LABEL, "wrong_key")
        .header(TENANT_ID_HEADER_LABEL, firstTenantId)
        .pathParameter("assetCode", ASSET_CODE)
        .pathParameter("issuer", issuer)
        .body(trustLine)
        .put("/modules/stellarbridge/trustlines/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  public void createTrustLineTrusteeDoesntExist() {
    final TrustLineConfiguration trustLine = new TrustLineConfiguration(BigDecimal.TEN);

    String issuer = "";
    try {
      issuer = URLEncoder.encode("blah*test.org", "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Assert.fail();
    }

    given().header(StellarBridgeTestHelpers.CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, firstTenantApiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, firstTenantId)
        .pathParameter("assetCode", ASSET_CODE)
        .pathParameter("issuer", issuer)
        .body(trustLine)
        .put("/modules/stellarbridge/trustlines/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.NOT_FOUND.value());
  }

  @Test
  public void createTrustLineHappyCase() {
    final String secondTenantId = UUID.randomUUID().toString();
    final String secondTenantApiKey = createAndDestroyBridge(secondTenantId, testCleanup, testRig.getMifosAddress());
    setVaultSize(secondTenantId, secondTenantApiKey, ASSET_CODE, BigDecimal.TEN);

    final String secondTenantStellarAddress = tenantVaultStellarAddress(secondTenantId);

    createAndDestroyTrustLine(firstTenantId, firstTenantApiKey,
        secondTenantStellarAddress, ASSET_CODE,
        BigDecimal.valueOf(1000), testCleanup);
  }
}
