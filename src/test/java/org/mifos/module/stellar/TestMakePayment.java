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

import com.jayway.restassured.RestAssured;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mifos.module.stellar.configuration.MifosStellarBridgeConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.mifos.module.stellar.StellarBridgeTestHelpers.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(MifosStellarBridgeConfiguration.class)
@WebIntegrationTest({
    "server.port=0", //randomPort = true
    "stellar.installation-account-private-key="
        + StellarDockerImage.MASTER_ACCOUNT_PRIVATE_KEY,
    "stellar.new-account-initial-balance=1020",
    "stellar.local-federation-domain=" + TEST_ADDRESS_DOMAIN
})
public class TestMakePayment {

  public static final String ASSET_CODE = "XXX";
  public static final BigDecimal CREDIT_LIMIT = BigDecimal.valueOf(1000);
  @Value("${local.server.port}")
  int bridgePort;

  private Cleanup testCleanup = new Cleanup();
  private final static Cleanup suiteCleanup = new Cleanup();
  private String firstTenantId;
  private String firstTenantApiKey;
  private String secondTenantId;
  private String secondTenantApiKey;

  @BeforeClass
  public static void setupSystem() throws IOException, InterruptedException {
    final StellarDockerImage stellarDockerImage = new StellarDockerImage();
    suiteCleanup.addStep(stellarDockerImage::close);

    stellarDockerImage.waitForStartupToComplete();

    System.setProperty("stellar.horizon-address", stellarDockerImage.address());
  }

  @Before
  public void setupTest() {
    RestAssured.port = bridgePort;

    firstTenantId = UUID.randomUUID().toString();
    firstTenantApiKey = createBridge(firstTenantId);
    testCleanup.addStep(() -> deleteBridge(firstTenantId, firstTenantApiKey));

    secondTenantId = UUID.randomUUID().toString();
    secondTenantApiKey = createBridge(secondTenantId);
    testCleanup.addStep(() -> deleteBridge(secondTenantId, secondTenantApiKey));

    createCreditLine(secondTenantId, secondTenantApiKey, firstTenantId, ASSET_CODE, CREDIT_LIMIT);
    testCleanup.addStep(
        () -> deleteCreditLine(secondTenantId, secondTenantApiKey, firstTenantId, ASSET_CODE));
  }

  @After
  public void tearDownTest() throws Exception {
    testCleanup.cleanup();
  }

  @AfterClass
  public static void tearDownSystem() throws Exception {
    suiteCleanup.cleanup();
  }

  @Test
  public void paymentHappyCase() throws InterruptedException {
    final BigDecimal transferAmount = BigDecimal.valueOf(10);

    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE,transferAmount);

    Thread.sleep(5000); //TODO: find a better way to determine when the payment is complete.

    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, transferAmount);
  }

  @Test
  public void paymentAboveCreditLimit() throws InterruptedException
  {
    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE,
        CREDIT_LIMIT.add(BigDecimal.ONE));

    Thread.sleep(5000); //TODO: find a better way to determine when the payment is complete.

    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, BigDecimal.ZERO);
  }

  /*@Test
  public void cancellingPayments() throws InterruptedException
  {
    final BigDecimal transferAmount = BigDecimal.valueOf(10);
    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE, transferAmount);
    makePayment(secondTenantId, secondTenantApiKey, firstTenantId, ASSET_CODE, transferAmount);

    Thread.sleep(5000); //TODO: find a better way to determine when the payment is complete.

    checkBalance(firstTenantId, firstTenantApiKey, ASSET_CODE, BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, BigDecimal.ZERO);
  }*/
}
