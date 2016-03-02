package org.fineract.module.stellar;

import com.jayway.restassured.RestAssured;
import org.fineract.module.stellar.restdomain.AmountConfiguration;
import org.fineract.module.stellar.restdomain.TrustLineConfiguration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.fineract.module.stellar.configuration.BridgeConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.given;
import static org.fineract.module.stellar.AccountBalanceMatcher.balanceMatches;
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
public class TestVault {
  public static final String ASSET_CODE = "XXX";
  public static final int MAX_PAY_WAIT = 20000;


  @Value("${local.server.port}")
  int bridgePort;

  @Value("${stellar.horizon-address}")
  String serverAddress;

  static FineractStellarTestRig testRig;

  private Cleanup testCleanup = new Cleanup();

  private String tenantId;
  private String tenantApiKey;

  public static void setVaultSizeWrong(
      final String tenantName,
      final String apiKey,
      final String assetCode,
      final BigDecimal requestedBalance,
      final BigDecimal finalBalance)
  {
    final AmountConfiguration amount = new AmountConfiguration(requestedBalance);

    given()
        .header(CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantName)
        .pathParameter("assetCode", assetCode)
        .body(amount)
        .put("/modules/stellarbridge/vault/{assetCode}/")
        .then().assertThat().statusCode(HttpStatus.CONFLICT.value())
        .content(balanceMatches(finalBalance));
  }

  public static void setVaultSizeNegative(
      final String tenantName,
      final String apiKey,
      final String assetCode,
      final BigDecimal requestedBalance)
  {
    final AmountConfiguration amount = new AmountConfiguration(requestedBalance);

    given()
        .header(CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantName)
        .pathParameter("assetCode", assetCode)
        .body(amount)
        .put("/modules/stellarbridge/vault/{assetCode}/")
        .then().assertThat().statusCode(HttpStatus.BAD_REQUEST.value())
        .content(balanceMatches(requestedBalance));
  }

  public static void checkVaultSize(
      final String tenantId,
      final String apiKey,
      final String assetCode,
      final BigDecimal balance) {
    given()
        .header(CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantId)
        .pathParameter("assetCode", assetCode)
        .get("/modules/stellarbridge/vault/{assetCode}/")
        .then().assertThat().statusCode(HttpStatus.OK.value())
        .content(balanceMatches(balance));
  }

  public static void checkTenantHasNoVault(
      final String tenantId,
      final String apiKey,
      final String assetCode) {
    given()
        .header(CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantId)
        .pathParameter("assetCode", assetCode)
        .get("/modules/stellarbridge/vault/{assetCode}/")
        .then().assertThat().statusCode(HttpStatus.NOT_FOUND.value());
  }

  @BeforeClass
  public static void setupSystem() throws Exception {
    testRig = new FineractStellarTestRig();
  }

  @Before
  public void setupTest() {
    RestAssured.port = bridgePort;

    tenantId = "default";
    tenantApiKey = createAndDestroyBridge(
        tenantId, testCleanup, testRig.getMifosAddress());
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
  public void setVaultSizeBackToZero()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.ZERO);

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.ZERO);

    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.ZERO);
  }

  @Test
  public void setVaultNegative()
  {
    setVaultSizeNegative(tenantId, tenantApiKey, ASSET_CODE,
        BigDecimal.valueOf(-10));

    checkBalanceDoesntExist(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId));

    checkTenantHasNoVault(tenantId, tenantApiKey, ASSET_CODE);
  }

  @Test
  public void setVaultSizeBackToNegative()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);
    setVaultSizeNegative(tenantId, tenantApiKey, ASSET_CODE,
        BigDecimal.valueOf(-10));

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.TEN);
    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);
  }


  //@Test
  public void setVaultSizeBelowPossible() throws Exception {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);

    final String secondTenantId = UUID.randomUUID().toString();
    final String secondTenantApiKey
        = createAndDestroyBridge(secondTenantId, testCleanup, testRig.getMifosAddress());

    createAndDestroyTrustLine(
        secondTenantId, secondTenantApiKey,
        tenantVaultStellarAddress(tenantId),
        ASSET_CODE, BigDecimal.TEN,
        testCleanup);

    final AccountListener accountListener = new AccountListener(serverAddress, secondTenantId);
    final BigDecimal transferAmount = BigDecimal.valueOf(5);
    makePayment(tenantId, tenantApiKey, secondTenantId, ASSET_CODE, transferAmount);

    accountListener.waitForCredits(MAX_PAY_WAIT,
        AccountListener.credit(secondTenantId, transferAmount, ASSET_CODE, tenantId));

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.valueOf(5));
    checkBalance(secondTenantId, secondTenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.valueOf(5));

    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);


    setVaultSizeWrong(tenantId, tenantApiKey, ASSET_CODE,
        BigDecimal.valueOf(2), BigDecimal.valueOf(5));

    checkBalance(tenantId, tenantApiKey, ASSET_CODE,
        tenantVaultStellarAddress(tenantId), BigDecimal.ZERO);

    checkVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.valueOf(5));

    //Zero out balance
    makePayment(secondTenantId, secondTenantApiKey, tenantId, ASSET_CODE, transferAmount);
    accountListener.waitForCredits(MAX_PAY_WAIT,
        AccountListener.credit(tenantId, transferAmount, ASSET_CODE, secondTenantId));
  }

  @Test
  public void setTrustlineToTenantsOwnVault()
  {
    setVaultSize(tenantId, tenantApiKey, ASSET_CODE, BigDecimal.TEN);

    final TrustLineConfiguration trustLine = new TrustLineConfiguration(BigDecimal.TEN);

    String issuer = "";
    try {
      issuer = URLEncoder.encode(tenantVaultStellarAddress(tenantId), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Assert.fail();
    }

    given().header(StellarBridgeTestHelpers.CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, tenantApiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantId)
        .pathParameter("assetCode", ASSET_CODE)
        .pathParameter("issuer", issuer)
        .body(trustLine)
        .put("/modules/stellarbridge/trustlines/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.BAD_REQUEST.value());
  }
}
