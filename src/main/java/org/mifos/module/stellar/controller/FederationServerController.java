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

import com.google.gson.Gson;
import org.mifos.module.stellar.federation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.stellar.sdk.federation.FederationResponse;

@RestController
public class FederationServerController {
  private final LocalFederationService federationService;
  private final Gson gson;

  @Autowired
  public FederationServerController(
      final LocalFederationService federationService,
      final Gson gson)
  {
    this.federationService = federationService;
    this.gson = gson;
  }

  @RequestMapping(value = "/federation/", method = RequestMethod.GET,
      produces = {"application/json"})
  public ResponseEntity<String> getId(
      @RequestParam("type") final String type,
      @RequestParam("q") final String nameToLookUp) throws InvalidStellarAddressException {

    if (!type.equalsIgnoreCase("name"))
    {
      return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    final StellarAddress stellarAddress = StellarAddress.parse(nameToLookUp);


    final StellarAccountId accountId = federationService.getAccountId(stellarAddress);

    final FederationResponse ret;
    if (accountId.getSubAccount().isPresent()) {
      ret = FederationResponseBuilder
          .accountInMemoField(stellarAddress.toString(), accountId.getPublicKey(),
              accountId.getSubAccount().get());
    }
    else
    {
      ret = FederationResponseBuilder.account(stellarAddress.toString(), accountId.getPublicKey());
    }

    return new ResponseEntity<>(gson.toJson(ret), HttpStatus.OK);
  }


  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleInvalidNameFormatException(
      @SuppressWarnings("unused") final InvalidStellarAddressException ex) {
    return ex.getMessage();
  }


  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleFederationFailedException(
      @SuppressWarnings("unused") final FederationFailedException ex) {
    return ex.getMessage();
  }
}
