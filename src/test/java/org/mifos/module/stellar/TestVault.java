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
public class TestVault {
  public static final String ASSET_CODE = "XXX";

  @Value("${local.server.port}")
  int bridgePort;

  private Cleanup testCleanup = new Cleanup();
  private final static Cleanup suiteCleanup = new Cleanup();

  private String tenantId;
  private String tenantApiKey;

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

    tenantId = UUID.randomUUID().toString();
    tenantApiKey = createAndDestroyBridge(tenantId, testCleanup);
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
  public void setVaultSizeHappyCase()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.TEN);
    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);
  }

  @Test
  public void setVaultSizeTwice()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.valueOf(100));

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.valueOf(100));
    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.valueOf(100));
  }

  @Test
  public void setVaultSizeZero()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.ZERO);

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.ZERO);

    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.ZERO);
  }

  @Test
  public void setVaultSizeNegative()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.valueOf(-10));

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.ZERO);
    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.ZERO);
  }


  @Test
  public void setVaultSizeBelowPossible() throws InterruptedException {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);

    final String secondTenantId = UUID.randomUUID().toString();
    final String secondTenantApiKey = createAndDestroyBridge(secondTenantId, testCleanup);

    createAndDestroyTrustLine(
        secondTenantId, secondTenantApiKey,
        tenantVaultStellarAddress(tenantId),
        ASSET_CODE, BigDecimal.TEN,
        testCleanup);

    makePayment(tenantId, tenantApiKey, secondTenantId, ASSET_CODE, BigDecimal.valueOf(5));

    waitForPaymentToComplete();

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.valueOf(5));
    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.valueOf(5));

    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);


    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.valueOf(2));

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.ZERO);

    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.valueOf(5));
  }
}
