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
package org.mifos.module.stellar.controller;

import org.mifos.module.stellar.federation.FederationFailedException;
import org.mifos.module.stellar.federation.InvalidStellarAddressException;
import org.mifos.module.stellar.federation.StellarAddress;
import org.mifos.module.stellar.restdomain.AccountBridgeConfiguration;
import org.mifos.module.stellar.restdomain.TrustLineConfiguration;
import org.mifos.module.stellar.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/modules")
public class StellarBridgeController {
  private static final String API_KEY_HEADER_LABEL = "X-Stellar-Bridge-API-Key";
  private static final String TENANT_ID_HEADER_LABEL = "X-Mifos-Platform-TenantId";

  private final SecurityService securityService;
  private StellarBridgeService stellarBridgeService;

  @Autowired
  public StellarBridgeController(
      final SecurityService securityService,
      final StellarBridgeService stellarBridgeService)
  {
    this.securityService = securityService;
    this.stellarBridgeService = stellarBridgeService;
  }

  @RequestMapping(value = "/stellar/payments", method = RequestMethod.POST,
                  consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<Void> sendStellarPayment(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @RequestHeader("X-Mifos-Entity") final String entity,
      @RequestHeader("X-Mifos-Action") final String action,
      @RequestBody final String payload)
      throws InvalidApiKeyException {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    if (entity.equalsIgnoreCase("JOURNALENTRY")
        && action.equalsIgnoreCase("CREATE")) {

      this.stellarBridgeService.sendPaymentToStellar(mifosTenantId, payload);
    }


    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/stellar/trustline", method = RequestMethod.POST,
                  consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<Void> createTrustLine(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @RequestBody final TrustLineConfiguration stellarTrustLineConfig)
      throws InvalidStellarAddressException
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    this.stellarBridgeService.createTrustLine(
        mifosTenantId,
        StellarAddress.parse(stellarTrustLineConfig.getTrustedAccount()),
        stellarTrustLineConfig.getTrustedCurrency(),
        stellarTrustLineConfig.getMaximumAmount());

    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/stellar/configuration", method = RequestMethod.POST,
                  consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<String> createStellarBridgeConfiguration(
      @RequestBody final AccountBridgeConfiguration stellarBridgeConfig)
  {
    final String mifosTenantId = stellarBridgeConfig.getMifosTenantId();
    if (this.stellarBridgeService.accountBridgeExistsForTenantId(mifosTenantId))
    {
      // HttpStatus.BAD_REQUEST is more widely recognized.  CONFLICT more semantically correct.
      // This line may require adjustment.
      // HttpStatus.SEE_OTHER would also be a valid response.
      return new ResponseEntity<>("Tenant " + mifosTenantId + " already exists!",
          HttpStatus.CONFLICT);
    }

    final String newApiKey = this.securityService.generateApiKey();
    stellarBridgeService.createStellarBridgeConfig(
        newApiKey,
        stellarBridgeConfig.getMifosTenantId(),
        stellarBridgeConfig.getMifosToken());

    return new ResponseEntity<>(newApiKey, HttpStatus.CREATED);
  }

  @RequestMapping(value = "/stellar/configuration/{mifosTenantId}", method = RequestMethod.DELETE,
                   consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<Void> deleteAccountBridgeConfiguration(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @PathVariable("mifosTenantId") final String mifosTenantId)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    if (stellarBridgeService.deleteAccountBridgeConfig(mifosTenantId))
    {
      return new ResponseEntity<>(HttpStatus.OK);
    }
    else
    {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @RequestMapping(value = "/stellar/account/balance/{assetCode}", method = RequestMethod.GET,
  consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<String> getStellarAccountBalance(
      @RequestHeader(API_KEY_HEADER_LABEL) final String apiKey,
      @RequestHeader(TENANT_ID_HEADER_LABEL) final String mifosTenantId,
      @PathVariable("assetCode") final String assetCode)
  {
    this.securityService.verifyApiKey(apiKey, mifosTenantId);

    return new ResponseEntity<>(
        stellarBridgeService.getBalance(mifosTenantId, assetCode).toString(),
        HttpStatus.OK);
  }

  @RequestMapping(value = "/stellar/account/{secretSeed}/balance/{assetCode}",
      method = RequestMethod.GET, consumes = {"application/json"}, produces = {"application/json"})
  public ResponseEntity<String> getInstallationAccountBalance(
      @PathVariable("secretSeed") final String accountSecretSeed,
      @PathVariable("assetCode") final String assetCode)
  {
    return new ResponseEntity<>(
        stellarBridgeService.getInstallationAccountBalance(accountSecretSeed, assetCode).toString(),
        HttpStatus.OK);

  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public void handleInvalidApiKeyException(
      @SuppressWarnings("unused") final InvalidApiKeyException ex) {
    //TODO: add message to error
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleInvalidConfigurationException(
      @SuppressWarnings("unused") final InvalidConfigurationException ex) {
    //TODO: add message to error
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleStellarAccountCreationFailedException(
      @SuppressWarnings("unused") final StellarAccountCreationFailedException ex) {
    //TODO: add message to error
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleInvalidStellarAddressException(
      @SuppressWarnings("unused") final InvalidStellarAddressException ex) {
    //TODO: add message to error
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleFederationFailedException(
      @SuppressWarnings("unused") final FederationFailedException ex) {
    //TODO: add message to error.
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public void handleStellarTrustLineCreationFailedException(
      @SuppressWarnings("unused") final StellarTrustLineCreationFailedException ex)
  {
    //TODO: figure out how to communicate missing funds problem to user.
    //TODO: add message to error.
  }
}
