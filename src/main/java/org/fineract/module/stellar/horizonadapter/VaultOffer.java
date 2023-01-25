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
package org.fineract.module.stellar.horizonadapter;

import org.fineract.module.stellar.federation.StellarAccountId;
import org.fineract.module.stellar.service.UnexpectedException;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.OfferResponse;
import org.stellar.sdk.responses.Page;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.fineract.module.stellar.horizonadapter.StellarAccountHelpers.getAssetCode;
import static org.fineract.module.stellar.horizonadapter.StellarAccountHelpers.getIssuer;
import shadow.okhttp3.OkHttpClient;

class VaultOffer {
  final String assetCode;
  final String buyingFromIssuer;

  VaultOffer(final String assetCode, final String buyingFromIssuer) {
    this.assetCode = assetCode;
    this.buyingFromIssuer = buyingFromIssuer;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof VaultOffer))
      return false;
    VaultOffer that = (VaultOffer) o;
    return Objects.equals(assetCode, that.assetCode) && Objects
        .equals(buyingFromIssuer, that.buyingFromIssuer);
  }

  @Override public int hashCode() {
    return Objects.hash(assetCode, buyingFromIssuer);
  }


  static Map<VaultOffer, Long> getVaultOffers(final Server server, final String accountId,
      final StellarAccountId vaultAccountId)
      throws InvalidConfigurationException
  {
    final KeyPair accountKeyPair = KeyPair.fromAccountId(accountId);

    final Map<VaultOffer, Long> ret = new HashMap<>();

    Page<OfferResponse> offers;
    try {
      offers = server.offers().forAccount(accountKeyPair.getAccountId()).execute();
    } catch (final Exception e) {
      throw InvalidConfigurationException.unreachableStellarServerAddress("");
    }


    while (offers != null && offers.getRecords() != null) {
      for (final OfferResponse offer : offers.getRecords())
      {
        final String buyingAssetCode = getAssetCode(offer.getBuying());
        final String buyingIssuer = getIssuer(offer.getBuying());

        final String sellingAssetCode= getAssetCode(offer.getSelling());

        if (buyingAssetCode.equals(sellingAssetCode)
            && buyingIssuer.equals(vaultAccountId.getPublicKey()))
        {
          ret.put(new VaultOffer(buyingAssetCode, buyingIssuer), offer.getId());
        }
      }

      try {
          OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .build();
          
            offers = ((offers.getLinks() == null) || (offers.getLinks().getNext() == null)) ?
                null : offers.getNextPage(client);
      } catch (final Exception e) {
        throw new UnexpectedException();
      }
    }

    return ret;
  }
}
