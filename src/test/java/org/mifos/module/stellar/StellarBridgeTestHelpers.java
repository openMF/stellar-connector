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
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import org.mifos.module.stellar.restdomain.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Collections;

import static com.jayway.restassured.RestAssured.given;
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

  /**
   * @return the api key used when accessing the tenantName
   */
  public static String createBridge(final String tenantName)
  {
    final AccountBridgeConfiguration newAccount =
        new AccountBridgeConfiguration(tenantName, "token_" + tenantName);
    final Response creationResponse
        = given().header(CONTENT_TYPE_HEADER)
        .body(newAccount).post("/modules/stellar/configuration");

    creationResponse
        .then().assertThat().statusCode(HttpStatus.CREATED.value());

    return creationResponse.getBody().as(String.class, ObjectMapperType.GSON);
  }

  public static void deleteBridge(final String tenantName, final String apiKey)
  {
    final Response deletionResponse =
        given().header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, apiKey)
            .delete("/modules/stellar/configuration/{mifosTenantId}",
                Collections.singletonMap("mifosTenantId", tenantName));

    deletionResponse
        .then().assertThat().statusCode(HttpStatus.OK.value());
  }

  public static void createCreditLine(
      final String fromTenant,
      final String fromTenantApiKey,
      final String toTenant,
      final String assetCode,
      final BigDecimal amount) {

    final String toTenantStellarAddress = tenantStellarAddress(toTenant);
    final TrustLineConfiguration trustLine =
        new TrustLineConfiguration(toTenantStellarAddress, assetCode, amount);

    given().header(StellarBridgeTestHelpers.CONTENT_TYPE_HEADER)
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL, fromTenantApiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, fromTenant).body(trustLine)
        .post("/modules/stellar/creditline").then().assertThat().statusCode(HttpStatus.CREATED.value());
  }

  public static void deleteCreditLine(
      final String fromTenant,
      final String fromTenantApiKey,
      final String toTenant,
      final String assetCode)
  {
    final String toTenantStellarAddress = tenantStellarAddress(toTenant);

    given()
        .header(StellarBridgeTestHelpers.API_KEY_HEADER_LABEL,fromTenantApiKey)
        .header(StellarBridgeTestHelpers.TENANT_ID_HEADER_LABEL, fromTenant)
        .pathParam("stellarAddress", toTenantStellarAddress)
        .pathParam("assetCode", assetCode)
        .delete("/modules/stellar/creditline/{stellarAddress}/{assetCode}")
        .then().assertThat().statusCode(HttpStatus.OK.value());
  }

  public static void makePayment(
      final String fromTenant,
      final String fromTenantApiKey,
      final String toTenant,
      final String assetCode,
      final BigDecimal transferAmount)
  {
    final String payment1 = getPaymentPayload(
        assetCode,
        transferAmount,
        TEST_ADDRESS_DOMAIN,
        toTenant);

    given().header(CONTENT_TYPE_HEADER)
        .header(API_KEY_HEADER_LABEL, fromTenantApiKey)
        .header(TENANT_ID_HEADER_LABEL, fromTenant)
        .header(ENTITY_HEADER_LABEL, ENTITY_HEADER_VALUE)
        .header(ACTION_HEADER_LABEL, ACTION_HEADER_VALUE)
        .body(payment1)
        .post("/modules/stellar/payments")
        .then().assertThat().statusCode(HttpStatus.CREATED.value());
  }

  public static void checkBalance(
      final String tenant,
      final String tenantApiKey,
      final String assetCode,
      final BigDecimal amount)
  {
    given().header(CONTENT_TYPE_HEADER)
        .header(API_KEY_HEADER_LABEL, tenantApiKey)
        .header(TENANT_ID_HEADER_LABEL, tenant)
        .pathParam("assetCode", assetCode)
        .get("/modules/stellar/account/balance/{assetCode}")
        .then().assertThat().statusCode(HttpStatus.OK.value())
        .content(balanceMatches(amount));
  }

  public static String tenantStellarAddress(final String tenantId)
  {
    return tenantId + "*" + TEST_ADDRESS_DOMAIN;
  }

  static String getPaymentPayload(
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

    return new Gson().toJson(payment);
  }
}
