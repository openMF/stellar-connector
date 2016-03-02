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
import org.fineract.module.stellar.configuration.BridgeConfiguration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.fineract.module.stellar.StellarBridgeTestHelpers.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(BridgeConfiguration.class)
@WebIntegrationTest({
    "server.port=0", //randomPort = true
    "stellar.installation-account-private-key="
        + StellarDockerImage.MASTER_ACCOUNT_PRIVATE_KEY,
    "stellar.new-account-initial-balance=1020",
    "stellar.local-federation-domain=" + TEST_ADDRESS_DOMAIN
})
public class TestPaymentInSimpleNetwork {

  public static final String ASSET_CODE = "XXX";
  public static final BigDecimal TRUST_LIMIT   = BigDecimal.valueOf(1000);
  public static final BigDecimal VAULT_BALANCE = BigDecimal.valueOf(10000);
  public static final int PAY_WAIT = 25000;


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

  @BeforeClass
  public static void setupSystem() throws Exception {
    testRig = new FineractStellarTestRig();
  }

  @Before
  public void setupTest() {
    RestAssured.port = bridgePort;

    firstTenantId = UUID.randomUUID().toString();
    firstTenantApiKey = createAndDestroyBridge(
        firstTenantId, testCleanup, testRig.getMifosAddress());
    setVaultSize(firstTenantId, firstTenantApiKey, ASSET_CODE, VAULT_BALANCE);
    final String firstTenantVaultAddress = tenantVaultStellarAddress(firstTenantId);
    logger.info("First tenant setup {} with vault size {}.", firstTenantId, VAULT_BALANCE);

    secondTenantId = UUID.randomUUID().toString();
    secondTenantApiKey = createAndDestroyBridge(
        secondTenantId, testCleanup, testRig.getMifosAddress());
    setVaultSize(secondTenantId, secondTenantApiKey, ASSET_CODE, VAULT_BALANCE);
    final String secondTenantVaultAddress = tenantVaultStellarAddress(secondTenantId);
    logger.info("Second tenant setup {} with vault size {}.", secondTenantId, VAULT_BALANCE);


    createAndDestroyTrustLine(
        secondTenantId, secondTenantApiKey, firstTenantVaultAddress, ASSET_CODE, TRUST_LIMIT,
        testCleanup);

    createAndDestroyTrustLine(
        firstTenantId, firstTenantApiKey, secondTenantVaultAddress, ASSET_CODE, TRUST_LIMIT,
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
  public void paymentAboveCreditLimit() throws Exception
  {
    logger.info("paymentAboveCreditLimit test begin");

    final AccountListener accountListener =
        new AccountListener(serverAddress, secondTenantId);

    BigDecimal transferAmount = TRUST_LIMIT.add(BigDecimal.ONE);
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);


    final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(PAY_WAIT,
        AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, firstTenantId));

    if (missingCredits.isEmpty())
      logger.info("Account was credited.");

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);
  }

  @Test
  public void cancellingPayments() throws Exception
  {
    logger.info("cancellingPayments test begin");

    final AccountListener accountListener =
        new AccountListener(serverAddress, firstTenantId, secondTenantId);

    final BigDecimal transferAmount = BigDecimal.TEN;
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);

    {
      final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(PAY_WAIT,
          AccountListener.credit(secondTenantId, BigDecimal.TEN, ASSET_CODE, firstTenantId));

      if (!missingCredits.isEmpty())
        logger.info("Missing credits after first payment: {}", missingCredits);
    }

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        transferAmount);

    makePayment(secondTenantId, secondTenantApiKey,
        firstTenantId,
        ASSET_CODE, transferAmount);

    {
      final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(PAY_WAIT*3,
          AccountListener.credit(firstTenantId, BigDecimal.TEN, ASSET_CODE, secondTenantId),
          AccountListener.credit(secondTenantId, BigDecimal.TEN, ASSET_CODE, secondTenantId),
          AccountListener.credit(firstTenantId, BigDecimal.TEN, ASSET_CODE, firstTenantId));

      if (!missingCredits.isEmpty())
        logger.info("Missing credits after second payment: {}", missingCredits);
    }

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        VAULT_BALANCE);

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        VAULT_BALANCE);
  }

  @Test
  public void overlappingPayments() throws Exception {
    logger.info("overlappingPayments test begin");


    final AccountListener accountListener =
        new AccountListener(serverAddress, firstTenantId, secondTenantId);

    final BigDecimal transferAmount = BigDecimal.TEN;
    final BigDecimal doubleTransferAmount = transferAmount.add(transferAmount);
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);
    makePayment(secondTenantId, secondTenantApiKey,
        firstTenantId,
        ASSET_CODE, doubleTransferAmount);

    final List<AccountListener.Credit> missingCredits = accountListener.waitForCredits(PAY_WAIT*3,
        AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, firstTenantId),
        AccountListener.credit(firstTenantId, doubleTransferAmount, ASSET_CODE, secondTenantId),
        AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, secondTenantId));

    if (!missingCredits.isEmpty())
      logger.info("Missing credits: " + missingCredits);

    checkBalance(firstTenantId, firstTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        transferAmount);
    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);

    //Return balances to zero for next test.
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);

    final List<AccountListener.Credit> missingCredits2 = accountListener.waitForCredits(PAY_WAIT,
        AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, firstTenantId));

    if (!missingCredits2.isEmpty())
      logger.info("Missing credits: " + missingCredits);
  }


  @Test
  public void paymentSumApproachesCreditLimit() throws Exception
  {
    logger.info("paymentSumApproachesCreditLimit test begin");

    final BigDecimal transferIncrement = BigDecimal.valueOf(99.99);
    final BigDecimal lastBit = BigDecimal.valueOf(0.1);

    final AccountListener accountListener =
        new AccountListener(serverAddress, firstTenantId, secondTenantId);

    //Approach the credit limit, then go back down to zero.
    Collections.nCopies(10, transferIncrement).parallelStream().forEach(
        (transferAmount) -> makePayment(firstTenantId, firstTenantApiKey, secondTenantId,
            ASSET_CODE, transferAmount));

    {
      final List<AccountListener.Credit> transfers = new ArrayList<>();
      transfers.addAll(Collections.nCopies(10,
          AccountListener.credit(secondTenantId, transferIncrement, ASSET_CODE, firstTenantId)));

      final List<AccountListener.Credit> leftOverTransfers =
          accountListener.waitForCredits(PAY_WAIT *5, transfers);

      if (!leftOverTransfers.isEmpty())
        logger.info("{} transfers not completed.", leftOverTransfers.size());
    }

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        VAULT_BALANCE);

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        transferIncrement.multiply(BigDecimal.TEN));


    Collections.nCopies(10, transferIncrement).parallelStream().forEach(
        (transferAmount) -> makePayment(secondTenantId, secondTenantApiKey, firstTenantId,
            ASSET_CODE, transferAmount));

    {
      final List<AccountListener.Credit> transfers = new ArrayList<>();
      transfers.addAll(Collections.nCopies(10,
          AccountListener.credit(firstTenantId, transferIncrement, ASSET_CODE, secondTenantId)));
      transfers.addAll(Collections.nCopies(10,
          AccountListener.credit(secondTenantId, transferIncrement, ASSET_CODE, secondTenantId)));
      transfers.addAll(Collections.nCopies(10,
          AccountListener.credit(firstTenantId, transferIncrement, ASSET_CODE, firstTenantId)));


      final List<AccountListener.Credit> leftOverTransfers =
          accountListener.waitForCredits(PAY_WAIT *15, transfers);

      if (!leftOverTransfers.isEmpty())
        logger.info("{} transfers not completed.", leftOverTransfers.size());
    }

    checkBalance(firstTenantId, firstTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);


    //Approach the credit limit again, then go to exactly the credit limit
    Collections.nCopies(10, transferIncrement).parallelStream().forEach(
        (transferAmount) -> makePayment(firstTenantId, firstTenantApiKey, secondTenantId,
            ASSET_CODE, transferAmount));
    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE,
        lastBit);

    {
      final List<AccountListener.Credit> transfers = new ArrayList<>();
      transfers.addAll(Collections.nCopies(10,
          AccountListener.credit(secondTenantId, transferIncrement, ASSET_CODE, firstTenantId)));
      transfers.add(AccountListener.credit(secondTenantId, lastBit, ASSET_CODE, firstTenantId));

      final List<AccountListener.Credit> leftOverTransfers
          = accountListener.waitForCredits(PAY_WAIT * 5, transfers);

      if (!leftOverTransfers.isEmpty())
        logger.info("Not all transfers completed.");
    }

    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        TRUST_LIMIT);


    //Now try to go over the credit limit.
    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE, lastBit);

    accountListener.waitForCredits(PAY_WAIT,
        AccountListener.credit(secondTenantId, lastBit, ASSET_CODE, firstTenantId));

    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        TRUST_LIMIT);

    //Zero out balance for next test.
    makePayment(secondTenantId, secondTenantApiKey, firstTenantId, ASSET_CODE, TRUST_LIMIT);
    {
      final List<AccountListener.Credit> leftOverTransfers= accountListener.waitForCredits(PAY_WAIT,
          AccountListener.credit(firstTenantId, TRUST_LIMIT, ASSET_CODE, secondTenantId));

      if (!leftOverTransfers.isEmpty())
        logger.info("Not all transfers completed.");
    }
  }

  //TODO: test that mifos balance was adjusted.
  //TODO: test transferring to a user account.
  //TODO: add a test for transferring XLM.
  //TODO: add a test with enough paths to provoke paging in find path pay.
  //TODO: add a test for installation balance.
  //TODO: add a test for simple balance.
  //TODO: add a test with a mock for external federation containing external domain addresses.
  //TODO: add a test which pays to an invalid stellar address.
  //TODO: still needed a test which stops and starts the bridge component, but makes transactions
  //TODO: against Stellar while the bridge component is down.
  //TODO: test for invalid parameters to create bridge.
}
