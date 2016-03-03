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
package org.fineract.module.stellar.controller;

import com.google.gson.Gson;
import org.fineract.module.stellar.restdomain.AmountConfiguration;
import org.fineract.module.stellar.restdomain.TrustLineConfiguration;
import org.fineract.module.stellar.service.SecurityService;
import org.fineract.module.stellar.federation.FederationFailedException;
import org.fineract.module.stellar.federation.InvalidStellarAddressException;
import org.fineract.module.stellar.federation.StellarAddress;
import org.fineract.module.stellar.horizonadapter.InvalidConfigurationException;
import org.fineract.module.stellar.horizonadapter.StellarAccountCreationFailedException;
import org.fineract.module.stellar.horizonadapter.StellarTrustlineAdjustmentFailedException;
import org.fineract.module.stellar.persistencedomain.PaymentPersistency;
import org.fineract.module.stellar.restdomain.AccountBridgeConfiguration;
import org.fineract.module.stellar.restdomain.JournalEntryData;
import org.fineract.module.stellar.service.BridgeService;
import org.fineract.module.stellar.service.SecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;

@RestController()
@RequestMapping("/modules/stellarbridge")
public class BridgeController {
  private static final String API_KEY_HEADER_LABEL = "X-Stellar-Bridge-API-Key";
  private static final String TENANT_ID_HEADER_LABEL = "X-Mifos-Platform-TenantId";

  private final SecurityService securityService;
  private BridgeService bridgeService;
  private final JournalEntryPaymentMapper journalEntryPaymentMapper;
  private final Gson gson;

  @Autowired
  public BridgeController(
      final SecurityService securityService,
      final BridgeService bridgeService,
      final JournalEntryPaymentMapper journalEntryPaymentMapper,
      final Gson gson)
  {
    this.securityService = securityService;
    this.bridgeService = bridgeService;
    this.journalEntryPaymentMapper = journalEntryPaymentMapper;
    this.gson = gson;
  }

  @RequestMapping(value = "", method = RequestMethod.POST,
                  consumes = {"application/json"}, produces = {"application/json"})
  @Transactional
  public ResponseEntity<String> createStellarBridgeConfiguration(
      @RequestBody final AccountBridgeConfiguration stellarBridgeConfig)
  {

    //TODO: check that name can make a valid stellar address.
    if (stellarBridgeConfig.getMifosTenantId() == null ||
        stellarBridgeConfig.getMifosToken() == null ||
        stellarBridgeConfig.getEndpoint() == null)
    {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    final String newApiKey
        = this.securityService.generateApiKey(stellarBridgeConfig.getMifosTenantId());

    bridgeService.createStellarBridgeConfig(
        stellarBridgeConfig.getMifosTenantId(),
        stellarBridgeConfig.getMifosToken(),
        stellarBridgeConfig.getEndpoint());


    return new ResponseEntity<>(newApiKey, HttpStatus.CREATED);
  }

  @RequestMapping(value = "", method = RequestMethod.DELETE,
      produces = {"application/json"})
  @Transactional
  public ResponseEntity<Void> deleteAccountBridgeConfiguration(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);
    this.securityService.removeApiKey(mifosTenantId);

    bridgeService.deleteAccountBridgeConfig(mifosTenantId);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(
      value = "/trustlines/{assetCode}/{issuer}/",
      method = RequestMethod.PUT,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<Void> adjustTrustLine(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @PathVariable("assetCode") final String trustedAssetCode,
      @PathVariable("issuer") final String urlEncodedIssuingStellarAddress,
      @RequestBody final TrustLineConfiguration stellarTrustLineConfig)
      throws InvalidStellarAddressException
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);


    final String issuingStellarAddress;
    try {
      issuingStellarAddress = URLDecoder.decode(urlEncodedIssuingStellarAddress, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    this.bridgeService.adjustTrustLine(
        mifosTenantId,
        StellarAddress.parse(issuingStellarAddress),
        trustedAssetCode,
        stellarTrustLineConfig.getMaximumAmount());

    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/vault/{assetCode}",
      method = RequestMethod.PUT,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<BigDecimal> adjustVaultIssuedAssets(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @PathVariable("assetCode") final String assetCode,
      @RequestBody final AmountConfiguration amountConfiguration)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);
    //TODO: add security for currency issuing.
    final BigDecimal amount = amountConfiguration.getAmount();
    if (amount.compareTo(BigDecimal.ZERO) < 0)
    {
      return new ResponseEntity<>(amount, HttpStatus.BAD_REQUEST);
    }

    final BigDecimal amountAdjustedTo
        = bridgeService.adjustVaultIssuedAssets(mifosTenantId, assetCode, amount);

    if (amountAdjustedTo.compareTo(amount) != 0)
      return new ResponseEntity<>(amountAdjustedTo, HttpStatus.CONFLICT);
    else
      return new ResponseEntity<>(amountAdjustedTo, HttpStatus.OK);
  }

  @RequestMapping(value = "/vault/{assetCode}",
      method = RequestMethod.GET,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<BigDecimal> getVaultIssuedAssets(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @PathVariable("assetCode") final String assetCode)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    if (!bridgeService.tenantHasVault(mifosTenantId))
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);

    final BigDecimal vaultIssuedAssets
        = bridgeService.getVaultIssuedAssets(mifosTenantId, assetCode);

    return new ResponseEntity<>(vaultIssuedAssets, HttpStatus.OK);
  }

  @RequestMapping(
      value = "/balances/{assetCode}",
      method = RequestMethod.GET,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<BigDecimal> getStellarAccountBalance(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @PathVariable("assetCode") final String assetCode)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    return new ResponseEntity<>(
        bridgeService.getBalance(mifosTenantId, assetCode),
        HttpStatus.OK);
  }

  @RequestMapping(
      value = "/balances/{assetCode}/{issuer}/",
      method = RequestMethod.GET,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<BigDecimal> getStellarAccountBalanceByIssuer(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @PathVariable("assetCode") final String assetCode,
      @PathVariable("issuer") final String urlEncodedIssuingStellarAddress)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    final String issuingStellarAddress;
    try {
      issuingStellarAddress = URLDecoder.decode(urlEncodedIssuingStellarAddress, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return new ResponseEntity<>(
        bridgeService.getBalanceByIssuer(
            mifosTenantId, assetCode,
            StellarAddress.parse(issuingStellarAddress)),
        HttpStatus.OK);
  }

  @RequestMapping(value = "/payments/", method = RequestMethod.POST,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<Void> sendStellarPayment(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @RequestHeader("X-Mifos-Entity") final String entity,
      @RequestHeader("X-Mifos-Action") final String action,
      @RequestBody final String payload)
      throws SecurityException {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    if (entity.equalsIgnoreCase("JOURNALENTRY")
        && action.equalsIgnoreCase("CREATE"))
    {
      final JournalEntryData journalEntry = gson.fromJson(payload, JournalEntryData.class);

      final PaymentPersistency payment =
          journalEntryPaymentMapper.mapToPayment(mifosTenantId, journalEntry);

      if (!payment.isStellarPayment)
      {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }

      this.bridgeService.sendPaymentToStellar(payment);
    }

    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }

  @RequestMapping(
      value = "/installationaccount/balances/{assetCode}/{issuer}/",
      method = RequestMethod.GET,
      consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<BigDecimal> getInstallationAccountBalance(
      @PathVariable("assetCode") final String assetCode,
      @PathVariable("issuer") final String issuingStellarAddress)
  {
    return new ResponseEntity<>(
        bridgeService.getInstallationAccountBalance(
            assetCode,
            StellarAddress.parse(issuingStellarAddress)
        ),
        HttpStatus.OK);

  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public String handleInvalidApiKeyException(
      final SecurityException ex) {
    return ex.getMessage();
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleInvalidConfigurationException(
      final InvalidConfigurationException ex) {
    return ex.getMessage();
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleStellarAccountCreationFailedException(
      final StellarAccountCreationFailedException ex) {
    return ex.getMessage();
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleInvalidStellarAddressException(
      final InvalidStellarAddressException ex) {
    return ex.getMessage();
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleFederationFailedException(
      final FederationFailedException ex) {
    return ex.getMessage();
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleInvalidJournalEntryException(
      final InvalidJournalEntryException ex) {
    return ex.getMessage();
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleStellarTrustlineAdjustmentFailedException(
      final StellarTrustlineAdjustmentFailedException ex)
  {
    return ex.getMessage();
  }
}
