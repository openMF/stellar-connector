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
import org.fineract.module.stellar.fineractadapter.Adapter;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.fineract.module.stellar.AccountListener.*;
import static org.fineract.module.stellar.AccountListener.vaultMatcher;
import static org.fineract.module.stellar.StellarBridgeTestHelpers.*;

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
public class TestPaymentInSimpleNetwork {

  public static final String ASSET_CODE = "XXX";
  public static final BigDecimal TRUST_LIMIT   = BigDecimal.valueOf(1000);
  public static final BigDecimal VAULT_BALANCE = BigDecimal.valueOf(10000);
  public static final int PAY_WAIT = 30000;

  static FineractStellarTestRig testRig;

  @Value("${local.server.port}")
  int bridgePort;

  @Value("${stellar.horizon-address}")
  String serverAddress;

  @Autowired Adapter adapter;

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
    RestAdapterProviderMockProvider.mockFineract(adapter, testRig.getMifosAddress());

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
    logger.info("TestPaymentInSimpleNetwork.tearDownTest");
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
        new AccountListener(serverAddress, secondTenantId, firstTenantId);

    BigDecimal transferAmount = TRUST_LIMIT.add(BigDecimal.ONE);
    makePayment(firstTenantId, firstTenantApiKey,
        secondTenantId,
        ASSET_CODE, transferAmount);


    accountListener.waitForCredits(PAY_WAIT,
        creditMatcher(secondTenantId, transferAmount, ASSET_CODE, firstTenantId));

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

    accountListener.waitForCredits(PAY_WAIT,
        creditMatcher(secondTenantId, BigDecimal.TEN, ASSET_CODE, firstTenantId));

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        transferAmount);

    makePayment(secondTenantId, secondTenantApiKey,
        firstTenantId,
        ASSET_CODE, transferAmount);

    accountListener.waitForCredits(PAY_WAIT,
        creditMatcher(firstTenantId, BigDecimal.TEN, ASSET_CODE,
            vaultMatcher(secondTenantId, firstTenantId)));

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

    accountListener
        .waitForCredits(PAY_WAIT,
            creditMatcher(firstTenantId, doubleTransferAmount, ASSET_CODE, secondTenantId),
            creditMatcher(secondTenantId, transferAmount, ASSET_CODE, vaultMatcher(firstTenantId, secondTenantId)),
            creditMatcher(secondTenantId, transferAmount, ASSET_CODE, secondTenantId));

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

    accountListener.waitForCredits(PAY_WAIT,
        creditMatcher(secondTenantId, transferAmount, ASSET_CODE, vaultMatcher(firstTenantId, secondTenantId)));
  }


  @Test
  public void paymentSumApproachesCreditLimit() throws Exception
  {
    logger.info("paymentSumApproachesCreditLimit test begin");

    final BigDecimal transferIncrement = BigDecimal.valueOf(99.99);
    final BigDecimal lastBit = BigDecimal.valueOf(0.1);

    final AccountListener accountListener =
        new AccountListener(serverAddress, firstTenantId, secondTenantId);

    //Approach the creditMatcher limit, then go back down to zero.
    Collections.nCopies(10, transferIncrement).parallelStream().forEach(
        (transferAmount) -> makePayment(firstTenantId, firstTenantApiKey, secondTenantId,
            ASSET_CODE, transferAmount));

    {
      final List<AccountListener.CreditMatcher> transfers = new ArrayList<>();
      transfers.addAll(Collections.nCopies(10,
          creditMatcher(secondTenantId, transferIncrement, ASSET_CODE, firstTenantId)));

      accountListener.waitForCredits(PAY_WAIT * 3, transfers);
    }

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        VAULT_BALANCE);

    checkBalance(secondTenantId, secondTenantApiKey,
        ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        transferIncrement.multiply(BigDecimal.TEN));


    logger.info("paymentSumApproachesCreditLimit transfers back");
    Collections.nCopies(10, transferIncrement).parallelStream().forEach(
        (transferAmount) -> makePayment(secondTenantId, secondTenantApiKey, firstTenantId,
            ASSET_CODE, transferAmount));

    {
      final List<AccountListener.CreditMatcher> transfers = new ArrayList<>();
      transfers.addAll(Collections.nCopies(10,
          creditMatcher(firstTenantId, transferIncrement, ASSET_CODE, vaultMatcher(firstTenantId, secondTenantId))));

      accountListener.waitForCredits(PAY_WAIT * 3, transfers);

      accountListener.waitForCreditsToAccumulate(PAY_WAIT * 3,
          creditMatcher(secondTenantId, transferIncrement.multiply(BigDecimal.TEN), ASSET_CODE,
              vaultMatcher(firstTenantId, secondTenantId)));

      accountListener.waitForCreditsToAccumulate(PAY_WAIT * 3,
          creditMatcher(firstTenantId, transferIncrement.multiply(BigDecimal.TEN), ASSET_CODE,
              vaultMatcher(firstTenantId, secondTenantId)));
    }

    checkBalance(firstTenantId, firstTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(secondTenantId),
        BigDecimal.ZERO);
    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        BigDecimal.ZERO);


    //Approach the creditMatcher limit again, then go to exactly the creditMatcher limit
    Collections.nCopies(10, transferIncrement).parallelStream().forEach(
        (transferAmount) -> makePayment(firstTenantId, firstTenantApiKey, secondTenantId,
            ASSET_CODE, transferAmount));
    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE,
        lastBit);

    {
      final List<AccountListener.CreditMatcher> transfers = new ArrayList<>();
      transfers.addAll(Collections.nCopies(10, creditMatcher(secondTenantId, transferIncrement, ASSET_CODE,
          firstTenantId)));
      transfers.add(creditMatcher(secondTenantId, lastBit, ASSET_CODE, firstTenantId));

      accountListener.waitForCredits(PAY_WAIT * 3, transfers);
    }

    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        TRUST_LIMIT);


    //Now try to go over the creditMatcher limit.
    makePayment(firstTenantId, firstTenantApiKey, secondTenantId, ASSET_CODE, lastBit);

    accountListener.waitForCredits(PAY_WAIT,
        creditMatcher(secondTenantId, lastBit, ASSET_CODE, vaultMatcher(firstTenantId, secondTenantId)));

    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE, tenantVaultStellarAddress(firstTenantId),
        TRUST_LIMIT);

    //Zero out balance for next test.
    makePayment(secondTenantId, secondTenantApiKey, firstTenantId, ASSET_CODE, TRUST_LIMIT);
    accountListener.waitForCredits(PAY_WAIT,
          creditMatcher(firstTenantId, TRUST_LIMIT, ASSET_CODE, vaultMatcher(firstTenantId, secondTenantId)));
  }

  //TODO: add a test for installation balance.
  //TODO: add a test for simple balance.
  //TODO: add a test for transferring XLM.
  //TODO: add a test which pays to an invalid stellar address.
  //TODO: add a test with a mock for external federation containing external domain addresses.

  //TODO: test transferring to a user account.
  //TODO: add a test with enough paths to provoke paging in find path pay.
  //TODO: still needed a test which stops and starts the bridge component, but makes transactions
  //TODO: need to add check of Mifos balance to all these test cases.
}
