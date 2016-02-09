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
import java.util.Collections;
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
  public static final BigDecimal TRUST_LIMIT   = BigDecimal.valueOf(1000);
  public static final BigDecimal VAULT_BALANCE = BigDecimal.valueOf(10000);

  @Value("${local.server.port}")
  int bridgePort;

  private Cleanup testCleanup = new Cleanup();
  private final static Cleanup suiteCleanup = new Cleanup();
  private String firstTenantId;
  private String firstTenantApiKey;
  private String secondTenantId;
  private String secondTenantApiKey;
  private String thirdTenantId;
  private String thirdTenantApiKey;

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

    thirdTenantId = UUID.randomUUID().toString();
    thirdTenantApiKey = createAndDestroyBridge(thirdTenantId, testCleanup);


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
    suiteCleanup.cleanup();
  }

  @Test
  public void paymentHappyCase() throws InterruptedException {
    final BigDecimal transferAmount = BigDecimal.TEN;

    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);

    waitForPaymentToComplete();

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        transferAmount);
  }

  @Test
  public void paymentAboveCreditLimit() throws InterruptedException
  {
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, TRUST_LIMIT.add(BigDecimal.ONE));

    waitForPaymentToComplete();

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
  }

  @Test
  public void cancellingPayments() throws InterruptedException
  {
    final BigDecimal transferAmount = BigDecimal.TEN;
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);
    makePayment(secondTenantId, secondTenantApiKey,
        firstTenantId,
        ASSET_CODE, transferAmount);

    waitForPaymentToComplete();

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
  }

  @Test
  public void overlappingPayments() throws InterruptedException {
    final BigDecimal transferAmount = BigDecimal.TEN;
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);
    makePayment(secondTenantId, secondTenantApiKey,
        firstTenantId,
        ASSET_CODE, transferAmount.add(transferAmount));

    waitForPaymentToComplete();

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        transferAmount);
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
  }

  @Test
  public void roundRobinPayments() throws InterruptedException {
    final BigDecimal transferAmount = BigDecimal.TEN;
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);
    makePayment(secondTenantId, secondTenantApiKey,
        thirdTenantId,
        ASSET_CODE, transferAmount);
    makePayment(thirdTenantId, thirdTenantApiKey,
        firstTenantId,
        ASSET_CODE, transferAmount);

    waitForPaymentToComplete();

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(thirdTenantId),
        BigDecimal.ZERO);

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(thirdTenantId),
        BigDecimal.ZERO);

    checkBalance(thirdTenantId, thirdTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
    checkBalance(thirdTenantId, thirdTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
  }


  @Test
  public void paymentSumApproachesCreditLimit() throws InterruptedException
  {
    //Approach the credit limit, then go back down to zero.
    Collections.nCopies(10, BigDecimal.valueOf(99.99))
        .parallelStream()
        .forEach((transferAmount) ->
            makePayment(firstTenantId, firstTenantApiKey,
                secondTenantId,
                ASSET_CODE, transferAmount));

    Collections.nCopies(10, BigDecimal.valueOf(99.99))
        .parallelStream()
        .forEach((transferAmount) ->
            makePayment(secondTenantId, secondTenantApiKey,
                firstTenantId,
                ASSET_CODE, transferAmount));

    waitForPaymentToComplete();
    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);


    //Approach the credit limit again, then go to exactly the credit limit
    Collections.nCopies(10, BigDecimal.valueOf(99.99))
        .parallelStream()
        .forEach((transferAmount) ->
            makePayment(firstTenantId, firstTenantApiKey,
                secondTenantId,
                ASSET_CODE, transferAmount));
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, BigDecimal.valueOf(0.1));

    waitForPaymentToComplete();
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        TRUST_LIMIT);


    //Now try to go over the credit limit.
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, BigDecimal.valueOf(0.1));

    waitForPaymentToComplete();
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        TRUST_LIMIT);
  }
}
