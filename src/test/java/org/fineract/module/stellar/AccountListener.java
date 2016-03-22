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
package org.fineract.module.stellar;

import com.google.common.base.Preconditions;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeCreditAlphaNum;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.requests.EffectsRequestBuilder;
import org.stellar.sdk.requests.EventListener;
import org.stellar.sdk.responses.effects.AccountCreditedEffectResponse;
import org.stellar.sdk.responses.effects.EffectResponse;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.fineract.module.stellar.StellarBridgeTestHelpers.getStellarAccountIdForTenantId;
import static org.fineract.module.stellar.StellarBridgeTestHelpers.getStellarVaultAccountIdForTenantId;

public class AccountListener {

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

  class Listener implements EventListener<EffectResponse> {

    @Override public void onEvent(final EffectResponse effect) {
      logger.info("OnEvent with cursor {}", effect.getPagingToken());

      synchronized (operationsAlreadySeen)
      {
        if (operationsAlreadySeen.contains(effect.getPagingToken()))
        {
          return;
        }

        operationsAlreadySeen.add(effect.getPagingToken());
      }

      if (effect instanceof AccountCreditedEffectResponse)
      {
        final String to = stellarIdToTenantId.get(effect.getAccount().getAccountId());
        final BigDecimal amount
            = BigDecimal.valueOf(Double.parseDouble(((AccountCreditedEffectResponse) effect).getAmount()));
        final Asset asset = ((AccountCreditedEffectResponse) effect).getAsset();
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


        final Credit credit = new Credit(to, amount, code, issuer);
        logger.info("adding credit to queue {}", credit);
        credits.add(credit);
      }
    }
  }

  private Logger logger = LoggerFactory.getLogger(AccountListener.class.getName());
  private final BlockingQueue<Credit> credits = new LinkedBlockingQueue<>();
  private final Set<String> operationsAlreadySeen = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Map<String, String> stellarIdToTenantId = new HashMap<>();
  private final Map<String, String> stellarVaultIdToTenantId = new HashMap<>();
  private final Map<String, String> tenantIdToStellarId = new HashMap<>();
  private final String targetTenantId;

  AccountListener(final String serverAddress, final String... tenantIds)
  {
    Preconditions.checkNotNull(tenantIds);
    Preconditions.checkState(tenantIds.length != 0);

    Arrays.asList(tenantIds).stream()
        .filter(tenantId -> tenantId != null)
        .map(tenantId -> new Pair<>(getStellarAccountIdForTenantId(tenantId), tenantId))
        .filter(pair -> pair.getKey().isPresent())
        .forEachOrdered(pair ->
        {
          stellarIdToTenantId.put(pair.getKey().get(), pair.getValue());
          tenantIdToStellarId.put(pair.getValue(), pair.getKey().get());
        }
        );


    Arrays.asList(tenantIds).stream()
        .filter(tenantId -> tenantId != null)
        .map(tenantId -> new Pair<>(getStellarVaultAccountIdForTenantId(tenantId), tenantId))
        .filter(pair -> pair.getKey().isPresent())
        .forEachOrdered(pair ->
            stellarVaultIdToTenantId.put(pair.getKey().get(), pair.getValue()));

    targetTenantId = tenantIds[0];

    //Listen for only the first account on the list (otherwise payment events are sent for both send and receive).
    installPaymentListener(serverAddress, tenantIdToStellarId.get(targetTenantId));
  }

  private void installPaymentListener(final String serverAddress, final String stellarAccountId) {

    final EffectsRequestBuilder effectsRequestBuilder
        = new EffectsRequestBuilder(URI.create(serverAddress));
    effectsRequestBuilder.forAccount(KeyPair.fromAccountId(stellarAccountId)).cursor("now");

    final Listener listener = new Listener();

    effectsRequestBuilder.stream(listener);
  }

  public List<Credit> waitForCredits(final long maxWait, final Credit... creditsExpected)
      throws Exception {
    return waitForCredits(maxWait, Arrays.asList(creditsExpected));
  }

  public List<Credit> waitForCredits(final long maxWait, final List<Credit> creditsExpected)
      throws Exception {
    logger.info("Waiting {} milliseconds for credits to tenant {}.", maxWait, targetTenantId);

    final List<Credit> incompleteCredits = new LinkedList<>(creditsExpected);

    final long startTime = new Date().getTime();

    try (final Cleanup cleanup = new Cleanup()) {
      while (!incompleteCredits.isEmpty()) {
        final Credit credit = credits.poll(maxWait, TimeUnit.MILLISECONDS);

        final long now = new Date().getTime();
        final long waitedSoFar = now - startTime;

        if (credit != null) {
          final boolean matched = incompleteCredits.remove(credit);
          if (!matched)
            cleanup.addStep(() -> credits.put(credit));
        }


        if ((waitedSoFar > maxWait) && credits.isEmpty()) {
          logger.info("Wait time for tenant {} is up, and there are {} incomplete credits {}",
              targetTenantId, incompleteCredits.size(), incompleteCredits);
          return incompleteCredits;
        }
      }

      logger.info("Wait time for tenant {} is up, and there are {} incomplete credits {}",
          targetTenantId, incompleteCredits.size(), incompleteCredits);
      return incompleteCredits;
    }
  }

  public BigDecimal waitForCreditsToAccumulate(final long maxWait, final Credit sumCreditExpected)
    throws Exception {
    logger.info("Waiting {} milliseconds for credit accumulation to tenant {}.", maxWait, targetTenantId);
    BigDecimal missingBalance = sumCreditExpected.amount;

    final long startTime = new Date().getTime();

    try (final Cleanup cleanup = new Cleanup()) {
      while (missingBalance.compareTo(BigDecimal.ZERO) > 0)
      {
        final Credit credit = credits.poll(maxWait, TimeUnit.MILLISECONDS);
        if (credit != null) {
          final boolean matched = sumCreditExpected.assetCode.equals(credit.assetCode) &&
              sumCreditExpected.issuingTenantVault.equals(credit.issuingTenantVault) &&
              sumCreditExpected.toTenantId.equals(credit.toTenantId);
          if (!matched)
            cleanup.addStep(() -> credits.put(credit));
          else
            missingBalance = missingBalance.subtract(credit.amount);
        }

        final long now = new Date().getTime();
        final long waitedSoFar = now - startTime;

        if ((waitedSoFar > maxWait) && credits.isEmpty()) {

          logger.info("Wait time for tenant {} is up, and there missing balance is {}",
              targetTenantId, missingBalance);
          return missingBalance;
        }
      }

      logger.info("Wait time for tenant {} is up, and there missing balance is {}",
          targetTenantId, missingBalance);
      return missingBalance;
    }
  }
}
