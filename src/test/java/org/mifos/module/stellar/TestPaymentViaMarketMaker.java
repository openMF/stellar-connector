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
public class TestPaymentViaMarketMaker {
  public static final String ASSET_CODE = "XXX";
  public static final BigDecimal TRUST_LIMIT   = BigDecimal.valueOf(1000);
  public static final BigDecimal VAULT_BALANCE = BigDecimal.valueOf(10000);
  public static final BigDecimal MARKET_SIZE = BigDecimal.valueOf(100);

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
    firstTenantApiKey = createAndDestroyBridge(firstTenantId, testCleanup);
    setVaultSize(firstTenantId, firstTenantApiKey, ASSET_CODE, VAULT_BALANCE);
    final String firstTenantVaultAddress = tenantVaultStellarAddress(firstTenantId);

    secondTenantId = UUID.randomUUID().toString();
    secondTenantApiKey = createAndDestroyBridge(secondTenantId, testCleanup);
    setVaultSize(secondTenantId, secondTenantApiKey, ASSET_CODE, VAULT_BALANCE);
    final String secondTenantVaultAddress = tenantVaultStellarAddress(firstTenantId);

    final String marketMakerTenantId = UUID.randomUUID().toString();
    final String marketMakerTenantApiKey = createAndDestroyBridge(marketMakerTenantId, testCleanup);

    createAndDestroyTrustLine(marketMakerTenantId, marketMakerTenantApiKey, firstTenantId, ASSET_CODE,
        TRUST_LIMIT, testCleanup);

    createAndDestroyTrustLine(marketMakerTenantId, marketMakerTenantApiKey, secondTenantId, ASSET_CODE,
        TRUST_LIMIT, testCleanup);

    createAndDestroyTrustLine(secondTenantId, secondTenantApiKey, marketMakerTenantId, ASSET_CODE,
        TRUST_LIMIT, testCleanup);

    createAndDestroyTrustLine(firstTenantId, firstTenantApiKey, marketMakerTenantId, ASSET_CODE,
        TRUST_LIMIT, testCleanup);

    StellarBridgeTestHelpers.makePayment(
        firstTenantId, firstTenantApiKey, marketMakerTenantId,
        ASSET_CODE, TRUST_LIMIT);
    StellarBridgeTestHelpers.makePayment(
        secondTenantId, secondTenantApiKey, marketMakerTenantId,
        ASSET_CODE, TRUST_LIMIT);

    createSameCurrencyPassiveOffer(marketMakerTenantId, marketMakerTenantApiKey,
        ASSET_CODE, MARKET_SIZE,
        firstTenantVaultAddress,
        secondTenantVaultAddress);

    createSameCurrencyPassiveOffer(marketMakerTenantId, marketMakerTenantApiKey,
        ASSET_CODE, MARKET_SIZE,
        secondTenantVaultAddress,
        firstTenantVaultAddress);
  }

  @After
  public void tearDownTest() throws Exception {
    testCleanup.cleanup();
  }

  @AfterClass
  public static void tearDownSystem() throws Exception {
    suiteCleanup.cleanup();
  }

  //@Test
  public void paymentAtMarketCapacity() throws InterruptedException
  {
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, MARKET_SIZE);

    waitForPaymentToComplete();

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        VAULT_BALANCE.subtract(MARKET_SIZE).subtract(MARKET_SIZE));

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        VAULT_BALANCE);
  }


  //@Test
  public void paymentAboveMarketCapacity() throws InterruptedException
  {
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, MARKET_SIZE.add(BigDecimal.ONE));

    waitForPaymentToComplete();

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        VAULT_BALANCE.subtract(MARKET_SIZE));

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        VAULT_BALANCE.subtract(MARKET_SIZE));
  }
}
