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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.fineract.module.stellar.StellarBridgeTestHelpers.*;
import static org.fineract.module.stellar.StellarBridgeTestHelpers.createAndDestroyTrustLine;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(BridgeConfiguration.class)
@WebIntegrationTest({
    "server.port=0", //randomPort = true
    "stellar.installation-account-private-key="
        + StellarDockerImage.MASTER_ACCOUNT_PRIVATE_KEY,
    "stellar.new-account-initial-balance=1020",
    "stellar.local-federation-domain=" + TEST_ADDRESS_DOMAIN
})
public class TestPaymentInIncompleteNetwork {

  public static final String ASSET_CODE = "XXX";
  public static final BigDecimal TRUST_LIMIT   = BigDecimal.valueOf(1000);
  public static final BigDecimal VAULT_BALANCE = BigDecimal.valueOf(10000);
  public static final int MAX_PAY_WAIT = 30000;

  @Value("${local.server.port}")
  int bridgePort;

  @Value("${stellar.horizon-address}")
  String serverAddress;

  static FineractStellarTestRig testRig;


  private Logger logger = LoggerFactory.getLogger(TestPaymentInSimpleNetwork.class.getName());
  private Cleanup testCleanup = new Cleanup();
  private String firstTenantId;
  private String firstTenantApiKey;
  private String secondTenantId;
  private String secondTenantApiKey;
  private String thirdTenantId;
  private String thirdTenantApiKey;

  @BeforeClass
  public static void setupSystem() throws Exception {
    testRig = new FineractStellarTestRig();
  }

  @Before
  public void setupTest() {
    RestAssured.port = bridgePort;

    firstTenantId = UUID.randomUUID().toString();
    firstTenantApiKey = createAndDestroyBridge(firstTenantId, testCleanup, testRig.getMifosAddress());
    setVaultSize(firstTenantId, firstTenantApiKey, ASSET_CODE, VAULT_BALANCE);
    final String firstTenantVaultAddress = tenantVaultStellarAddress(firstTenantId);
    logger.info("First tenant setup {} with vault size {}.", firstTenantId, VAULT_BALANCE);

    secondTenantId = UUID.randomUUID().toString();
    secondTenantApiKey = createAndDestroyBridge(secondTenantId, testCleanup, testRig.getMifosAddress());
    setVaultSize(secondTenantId, secondTenantApiKey, ASSET_CODE, VAULT_BALANCE);
    final String secondTenantVaultAddress = tenantVaultStellarAddress(secondTenantId);
    logger.info("Second tenant setup {} with vault size {}.", secondTenantId, VAULT_BALANCE);

    thirdTenantId = UUID.randomUUID().toString();
    thirdTenantApiKey = createAndDestroyBridge(thirdTenantId, testCleanup, testRig.getMifosAddress());
    logger.info("Third tenant setup {} without vault.", thirdTenantId);


    createAndDestroyTrustLine(
        secondTenantId, secondTenantApiKey, firstTenantVaultAddress, ASSET_CODE, TRUST_LIMIT,
        testCleanup);

    createAndDestroyTrustLine(
        firstTenantId, firstTenantApiKey, secondTenantVaultAddress, ASSET_CODE, TRUST_LIMIT,
        testCleanup);

    createAndDestroyTrustLine(
        thirdTenantId, thirdTenantApiKey, secondTenantVaultAddress, ASSET_CODE, TRUST_LIMIT,
        testCleanup);
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
  public void paymentWithoutPathFails() throws Exception {
    logger.info("paymentWithoutPathFails test begin");

    final AccountListener accountListener =
        new AccountListener(serverAddress, thirdTenantId);

    makePayment(firstTenantId, firstTenantApiKey, thirdTenantId, ASSET_CODE, BigDecimal.TEN);

    final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(
        MAX_PAY_WAIT,
        AccountListener.credit(secondTenantId, BigDecimal.TEN, ASSET_CODE, thirdTenantId));

    if (missingCredits.isEmpty())
      logger.info("A credit which shouldn't have happened did.");

    checkBalance(thirdTenantId, thirdTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
  }

  @Test
  public void roundRobinPayments() throws Exception {
    logger.info("roundRobinPayments test begin");

    final AccountListener accountListener =
        new AccountListener(serverAddress, firstTenantId, secondTenantId, thirdTenantId);

    final BigDecimal transferAmount = BigDecimal.TEN;
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);
    makePayment(secondTenantId, secondTenantApiKey,
        thirdTenantId,
        ASSET_CODE, transferAmount);

    {
      final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(
          MAX_PAY_WAIT,
          AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, firstTenantId),
          AccountListener.credit(thirdTenantId, transferAmount, ASSET_CODE, secondTenantId));

      if (!missingCredits.isEmpty())
        logger.info("Missing credits: " + missingCredits);
    }

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        transferAmount);
    checkBalance(thirdTenantId, thirdTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        transferAmount);


    makePayment(thirdTenantId, thirdTenantApiKey,
        firstTenantId,
        ASSET_CODE, transferAmount);

    {
      final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(
          MAX_PAY_WAIT*5,
          AccountListener.credit(firstTenantId, transferAmount, ASSET_CODE, secondTenantId),
          AccountListener.credit(firstTenantId, transferAmount, ASSET_CODE, firstTenantId),
          AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, secondTenantId));

      if (!missingCredits.isEmpty())
        logger.info("Missing credits: " + missingCredits);
    }

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
    checkBalance(thirdTenantId, thirdTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
    checkBalance(thirdTenantId, thirdTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
  }
}
