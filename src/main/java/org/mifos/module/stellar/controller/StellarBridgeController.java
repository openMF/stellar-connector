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

import org.mifos.module.stellar.restdomain.AccountBridgeConfiguration;
import org.mifos.module.stellar.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/modules")
public class StellarBridgeController {
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
      @RequestHeader("X-Stellar-Bridge-API-Key") final String apiKey,
      @RequestHeader("X-Mifos-Platform-TenantId") final String mifosTenantId,
      @RequestHeader("X-Mifos-Entity") final String entity,
      @RequestHeader("X-Mifos-Action") final String action,
      @RequestBody final String payload)
      throws InvalidApiKeyException {
    this.securityService.verifyApiKey(apiKey);

    if (entity.equalsIgnoreCase("JOURNALENTRY")
        && action.equalsIgnoreCase("CREATE")) {

      this.stellarBridgeService.sendPaymentToStellar(mifosTenantId, payload);
    }


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
      @RequestHeader("X-Stellar-Bridge-API-Key") final String apiKey,
      @PathVariable("mifosTenantId") final String mifosTenantId)
  {
    this.securityService.verifyApiKey(apiKey);

    if (stellarBridgeService.deleteAccountBridgeConfig(mifosTenantId))
    {
      return new ResponseEntity<>(HttpStatus.OK);
    }
    else
    {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public void handleInvalidApiKeyException(
      @SuppressWarnings("unused") final InvalidApiKeyException ex) {
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleInvalidConfigurationException(
      @SuppressWarnings("unused") final InvalidConfigurationException ex) {
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleStellarAccountCreationFailedException(
      @SuppressWarnings("unused") final StellarAccountCreationFailedException ex) {

  }
}
