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

import javafx.util.Pair;
import org.stellar.base.Asset;
import org.stellar.base.AssetTypeCreditAlphaNum;
import org.stellar.base.KeyPair;
import org.stellar.sdk.effects.AccountCreditedEffect;
import org.stellar.sdk.effects.Effect;
import org.stellar.sdk.requests.EffectsRequestBuilder;
import org.stellar.sdk.requests.EventListener;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.mifos.module.stellar.StellarBridgeTestHelpers.getStellarAccountIdForTenantId;
import static org.mifos.module.stellar.StellarBridgeTestHelpers.getStellarVaultAccountIdForTenantId;

public class PaymentListener {
  static class Credit
  {
    private final String toTenantId;
    private final BigDecimal amount;
    private final String assetCode;
    private final String issuingTenantVault;

    public Credit(final String toTenantId,
        final BigDecimal amount,
        final String assetCode, final String issuingTenantVault) {
      this.toTenantId = toTenantId;
      this.amount = amount;
      this.assetCode = assetCode;
      this.issuingTenantVault = issuingTenantVault;
    }

    @Override public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof Credit))
        return false;
      Credit credit = (Credit) o;
      return Objects.equals(toTenantId, credit.toTenantId) &&
          Objects.equals(assetCode, credit.assetCode) &&
          (amount.compareTo(credit.amount) == 0) &&
          Objects.equals(issuingTenantVault, credit.issuingTenantVault);
    }

    @Override public int hashCode() {
      return Objects.hash(toTenantId, amount, assetCode, issuingTenantVault);
    }

    @Override public String toString() {
      return "Credit{" +
          "toTenantId='" + toTenantId + '\'' +
          ", amount=" + amount +
          ", asset='" + assetCode + '@' +
          issuingTenantVault + '\'' +
          '}';
    }
  }

  static Credit credit(final String toTenantId,
      final BigDecimal amount,
      final String assetCode, final String issuingTenantVault)
  {
    return new Credit(toTenantId, amount, assetCode, issuingTenantVault);
  }

  class Listener implements EventListener<Effect> {

    @Override public void onEvent(final Effect effect) {
      if (effect instanceof AccountCreditedEffect)
      {
        final String to = stellarIdToTenantId.get(effect.getAccount().getAccountId());
        final BigDecimal amount
            = BigDecimal.valueOf(Double.parseDouble(((AccountCreditedEffect) effect).getAmount()));
        final Asset asset = ((AccountCreditedEffect) effect).getAsset();
        final String code;
        final String issuer;
        if (asset instanceof AssetTypeCreditAlphaNum)
        {
          code = ((AssetTypeCreditAlphaNum) asset).getCode();
          issuer = stellarVaultIdToTenantId
              .get(((AssetTypeCreditAlphaNum) asset).getIssuer().getAccountId());
        }
        else
        {
          code = "XLM";
          issuer = "";
        }


        credits.add(new Credit(to, amount, code, issuer));
      }

    }
  }

  final BlockingQueue<Credit> credits = new LinkedBlockingQueue<>();
  final Map<String, String> stellarIdToTenantId = new HashMap<>();
  final Map<String, String> stellarVaultIdToTenantId = new HashMap<>();

  PaymentListener(final String serverAddress, final String... tenantIds)
  {
    Arrays.asList(tenantIds).stream()
        .filter(tenantId -> tenantId != null)
        .map(tenantId -> new Pair<>(getStellarAccountIdForTenantId(tenantId), tenantId))
        .filter(pair -> pair.getKey().isPresent())
        .forEachOrdered(pair ->
            stellarIdToTenantId.put(pair.getKey().get(), pair.getValue()));


    Arrays.asList(tenantIds).stream()
        .filter(tenantId -> tenantId != null)
        .map(tenantId -> new Pair<>(getStellarVaultAccountIdForTenantId(tenantId), tenantId))
        .filter(pair -> pair.getKey().isPresent())
        .forEachOrdered(pair ->
            stellarVaultIdToTenantId.put(pair.getKey().get(), pair.getValue()));

    stellarIdToTenantId.entrySet().stream().forEach(
        mapEntry -> installPaymentListener(serverAddress, mapEntry.getKey())
    );
  }

  private void installPaymentListener(final String serverAddress, final String stellarAccountId) {

    final EffectsRequestBuilder effectsRequestBuilder
        = new EffectsRequestBuilder(URI.create(serverAddress));
    effectsRequestBuilder.forAccount(KeyPair.fromAccountId(stellarAccountId));

    final Listener listener = new Listener();

    effectsRequestBuilder.stream(listener);
  }

  public List<Credit> waitForCreditsToArrive(final long maxWait, final Credit... creditsExpected)
      throws InterruptedException {
    final List<Credit> incompleteCredits = new LinkedList<>(Arrays.asList(creditsExpected));

    final long startTime = new Date().getTime();

    while (!incompleteCredits.isEmpty()) {
      final Credit credit = credits.poll(maxWait/incompleteCredits.size(), TimeUnit.MILLISECONDS);
      if (credit != null) {
        incompleteCredits.remove(credit);
      }

      final long now = new Date().getTime();

      if (((now - startTime) > maxWait) && credits.isEmpty())
        return incompleteCredits;
    }

    return incompleteCredits;
  }
}
