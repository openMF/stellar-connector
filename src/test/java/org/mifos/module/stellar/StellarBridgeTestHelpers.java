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

import com.google.gson.Gson;
import com.jayway.restassured.internal.mapper.ObjectMapperType;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.mifos.module.stellar.restdomain.*;
import org.springframework.http.HttpStatus;
import org.stellar.sdk.federation.FederationResponse;

import javax.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.Optional;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mifos.module.stellar.AccountBalanceMatcher.balanceMatches;

public class StellarBridgeTestHelpers {
  public static final String API_KEY_HEADER_LABEL = "X-Stellar-Bridge-API-Key";
  public static final String TENANT_ID_HEADER_LABEL = "X-Mifos-Platform-TenantId";
  public static final String ENTITY_HEADER_LABEL = "X-Mifos-Entity";
  public static final String ENTITY_HEADER_VALUE = "JOURNALENTRY";
  public static final String ACTION_HEADER_LABEL = "X-Mifos-Action";
  public static final String ACTION_HEADER_VALUE = "CREATE";
  public static final Header CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  public static final String TEST_ADDRESS_DOMAIN = "test.org";
  public static final String STELLAR_ROUTING_CODE = "STELLAR";

  /**
   * @return the api key used when accessing the tenantName
   */
  public static String createAndDestroyBridge(final String tenantName, final Cleanup testCleanup,
      final String endpoint)
  {
    final String apiKey = createBridge(tenantName, endpoint);
    testCleanup.addStep(() -> deleteBridge(tenantName, apiKey));
    return apiKey;
  }

  private static String createBridge(final String tenantName, final String mifosAddress)
  {
    final AccountBridgeConfiguration newAccount =
        new AccountBridgeConfiguration(tenantName, getTenantToken(tenantName, mifosAddress), mifosAddress);
    final Response creationResponse =
        given()
            .header(CONTENT_TYPE_HEADER)
            .body(newAccount)
            .post("/modules/stellarbridge");

    creationResponse
        .then().assertThat().statusCode(HttpStatus.CREATED.value());

    return creationResponse.getBody().as(String.class, ObjectMapperType.GSON);
  }

  private static String getTenantToken(final String tenantName, final String mifosAddress) {
    final String encodedTenantName;
    try {
      encodedTenantName = URLEncoder.encode(tenantName, "UTF-8");
    } catch (UnsupportedEncodingException ignore) {
      throw new RuntimeException("unexpected error.");
    }
    final String LOGIN_URL = "/fineract-provider/api/v1/authentication?username=mifos&password=password&tenantIdentifier=" + encodedTenantName;

    final String json = given().baseUri(mifosAddress).post(LOGIN_URL).asString();

    assertThat("Failed to login into fineract platform", StringUtils.isBlank(json), is(false));
    final String ret = JsonPath.with(json).get("base64EncodedAuthenticationKey");
    assertThat("Failed to acquire a tenant token", StringUtils.isBlank(ret), is(false));
    return ret;
  }

  public static void deleteBridge(final String tenantName, final String apiKey)
  {
    final Response deletionResponse =
        given()
            .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
            .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantName)
            .delete("/modules/stellarbridge");

    deletionResponse
        .then().assertThat().statusCode(HttpStatus.OK.value());
  }

  public static void setVaultSize(
      final String tenantName,
      final String apiKey,
      final String assetCode,
      final BigDecimal balance)
  {
    final AmountConfiguration amount = new AmountConfiguration(balance);

    given()
        .header(CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, tenantName)
        .pathParameter("assetCode", assetCode)
        .body(amount)
        .put("/modules/stellarbridge/vault/{assetCode}/")
        .then().assertThat().statusCode(HttpStatus.OK.value())
        .content(balanceMatches(balance));
  }

  public static void createAndDestroyTrustLine(
      final String fromTenant, final String fromTenantApiKey,
      final String toStellarAddress, final String assetCode, final BigDecimal amount,
      final Cleanup testCleanup) {
    createTrustLine(fromTenant, fromTenantApiKey, toStellarAddress, assetCode, amount);
    testCleanup.addStep(
        () -> deleteTrustLine(
            fromTenant, fromTenantApiKey, toStellarAddress, assetCode));
  }

  private static void createTrustLine(
      final String fromTenant, final String fromTenantApiKey,
      final String toStellarAddress,
      final String assetCode,
      final BigDecimal amount) {
    final TrustLineConfiguration trustLine = new TrustLineConfiguration(amount);

    String issuer = "";
    try {
      issuer = URLEncoder.encode(toStellarAddress, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Assert.fail();
    }

    given().header(StellarBridgeTestHelpers.CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, fromTenantApiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, fromTenant)
        .pathParameter("assetCode", assetCode)
        .pathParameter("issuer", issuer)
        .body(trustLine)
        .put("/modules/stellarbridge/trustlines/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.OK.value());
  }

  public static void deleteTrustLine(
      final String fromTenant,
      final String fromTenantApiKey,
      final String toStellarAddress,
      final String assetCode)
  {
    String issuer = "";
    try {
      issuer = URLEncoder.encode(toStellarAddress, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Assert.fail();
    }

    given().header(StellarBridgeTestHelpers.CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL,fromTenantApiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, fromTenant)
        .pathParam("assetCode", assetCode)
        .pathParam("issuer", issuer)
        .body(new TrustLineConfiguration(BigDecimal.ZERO))
        .put("/modules/stellarbridge/trustlines/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.OK.value());
  }

  public static void makePayment(
      final String fromTenant,
      final String fromTenantApiKey,
      final String toTenant,
      final String assetCode,
      final BigDecimal transferAmount)
  {
    final String payment = getPaymentPayload(
        assetCode,
        transferAmount,
        TEST_ADDRESS_DOMAIN,
        toTenant);

    given().header(CONTENT_TYPE_HEADER)
        .header(API_KEY_HEADER_LABEL, fromTenantApiKey)
        .header(TENANT_ID_HEADER_LABEL, fromTenant)
        .header(ENTITY_HEADER_LABEL, ENTITY_HEADER_VALUE)
        .header(ACTION_HEADER_LABEL, ACTION_HEADER_VALUE)
        .body(payment)
        .post("/modules/stellarbridge/payments/")
        .then().assertThat().statusCode(HttpStatus.ACCEPTED.value());
  }

  public static void checkBalance(
      final String tenant,
      final String tenantApiKey,
      final String assetCode,
      final String issuingStellarAddress,
      final BigDecimal amount)
  {
    String issuer = "";
    try {
      issuer = URLEncoder.encode(issuingStellarAddress, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Assert.fail();
    }

    given().header(CONTENT_TYPE_HEADER)
        .header(API_KEY_HEADER_LABEL, tenantApiKey)
        .header(TENANT_ID_HEADER_LABEL, tenant)
        .pathParam("assetCode", assetCode)
        .pathParam("issuer", issuer)
        .get("/modules/stellarbridge/balances/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.OK.value())
        .content(balanceMatches(amount));
  }

  public static void checkBalanceDoesntExist(
      final String tenant,
      final String tenantApiKey,
      final String assetCode,
      final String issuingStellarAddress)
  {
    String issuer = "";
    try {
      issuer = URLEncoder.encode(issuingStellarAddress, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Assert.fail();
    }

    given().header(CONTENT_TYPE_HEADER)
        .header(API_KEY_HEADER_LABEL, tenantApiKey)
        .header(TENANT_ID_HEADER_LABEL, tenant)
        .pathParam("assetCode", assetCode)
        .pathParam("issuer", issuer)
        .get("/modules/stellarbridge/balances/{assetCode}/{issuer}/")
        .then().assertThat().statusCode(HttpStatus.NOT_FOUND.value());
  }

  public static String tenantVaultStellarAddress(final String tenantId)
  {
    return tenantId + ":vault" + "*" + TEST_ADDRESS_DOMAIN;
  }

  public static String tenantStellarAddress(final String tenantId) {

    return tenantId + "*" + TEST_ADDRESS_DOMAIN;
  }

  public static Optional<String> getStellarAccountIdForTenantId(@NotNull final String tenantId) {

    final String tenantStellarAddress = tenantStellarAddress(tenantId);

    return getStellarAccountIdForStellarAddress(tenantStellarAddress);
  }

  public static Optional<String> getStellarVaultAccountIdForTenantId(@NotNull final String tenantId) {
    final String tenantVaultStellarAddress = tenantVaultStellarAddress(tenantId);

    return getStellarAccountIdForStellarAddress(tenantVaultStellarAddress);
  }

  public static Optional<String> getStellarAccountIdForStellarAddress
      (@NotNull final String tenantStellarAddress) {
    final Response restResponse = given()
        .queryParam("type", "name").queryParam("q", tenantStellarAddress).get("/federation/");

    int statusCode = restResponse.getStatusCode();
    if (statusCode != HttpStatus.OK.value())
    {
      return Optional.empty();
    }

    final FederationResponse response
        = restResponse.getBody().as(FederationResponse.class, ObjectMapperType.GSON);

    if (response == null)
      return Optional.empty();

    return Optional.of(response.getAccountId());
  }


  private static String getPaymentPayload(
      final String assetCode,
      final BigDecimal amount,
      final String toDomain,
      final String toTenant) {
    final JournalEntryData payment = new JournalEntryData();
    payment.currency = new CurrencyData();
    payment.currency.inMultiplesOf = 1;
    payment.currency.code = assetCode;
    payment.amount = amount;
    payment.transactionDetails = new TransactionDetailData();
    payment.transactionDetails.paymentDetails = new PaymentDetailData();
    payment.transactionDetails.paymentDetails.bankNumber = toDomain;
    payment.transactionDetails.paymentDetails.accountNumber = toTenant;
    payment.transactionDetails.paymentDetails.routingCode = STELLAR_ROUTING_CODE;

    return new Gson().toJson(payment);
  }
}
