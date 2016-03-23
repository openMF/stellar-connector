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

    private Credit(final String toTenantId,
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

  static class CreditMatcher
  {
    private final String toTenantId;
    private final BigDecimal amount;
    private final String assetCode;
    private final Set<String> issuingTenantVaults;

    private CreditMatcher(final String toTenantId,
        final BigDecimal amount,
        final String assetCode,
        final Set<String> issuingTenantVaults) {
      this.toTenantId = toTenantId;
      this.amount = amount;
      this.assetCode = assetCode;
      this.issuingTenantVaults = issuingTenantVaults;
    }

    boolean matches(final Credit credit)
    {
      return toTenantId.equals(credit.toTenantId)  &&
          amount.compareTo(credit.amount) == 0 &&
          assetCode.equals(credit.assetCode) &&
          issuingTenantVaults.contains(credit.issuingTenantVault);
    }

    @Override public String toString() {
      return "CreditMatcher{" +
          "toTenantId='" + toTenantId + '\'' +
          ", amount=" + amount +
          ", assetCode='" + assetCode + '\'' +
          ", issuingTenantVaults=" + issuingTenantVaults +
          '}';
    }
  }

  static Set<String> vaultMatcher(final String... issuingTenantVaults)
  {
    final Set<String> ret = new HashSet<>();

    Collections.addAll(ret, issuingTenantVaults);

    return ret;
  }

  static CreditMatcher creditMatcher(final String toTenantId,
      final BigDecimal amount,
      final String assetCode, final Set<String> issuingTenantVaults)
  {
    return new CreditMatcher(toTenantId, amount, assetCode, issuingTenantVaults);
  }

  static CreditMatcher creditMatcher(final String toTenantId,
      final BigDecimal amount,
      final String assetCode, final String issuingTenantVaults)
  {
    return new CreditMatcher(toTenantId, amount, assetCode, vaultMatcher(issuingTenantVaults));
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

  AccountListener(final String serverAddress, final String... tenantIds)
  {
    Preconditions.checkNotNull(tenantIds);
    Preconditions.checkState(tenantIds.length != 0);

    Arrays.asList(tenantIds).stream()
        .filter(tenantId -> tenantId != null)
        .map(tenantId -> new Pair<>(getStellarAccountIdForTenantId(tenantId), tenantId))
        .filter(pair -> pair.getKey().isPresent())
        .forEachOrdered(pair ->
          stellarIdToTenantId.put(pair.getKey().get(), pair.getValue())
        );


    Arrays.asList(tenantIds).stream()
        .filter(tenantId -> tenantId != null)
        .map(tenantId -> new Pair<>(getStellarVaultAccountIdForTenantId(tenantId), tenantId))
        .filter(pair -> pair.getKey().isPresent())
        .forEachOrdered(pair ->
            stellarVaultIdToTenantId.put(pair.getKey().get(), pair.getValue()));

    stellarIdToTenantId.entrySet().stream().forEach(
        mapEntry -> installPaymentListener(serverAddress, mapEntry.getKey()));
  }

  private void installPaymentListener(final String serverAddress, final String stellarAccountId) {

    final EffectsRequestBuilder effectsRequestBuilder
        = new EffectsRequestBuilder(URI.create(serverAddress));
    effectsRequestBuilder.forAccount(KeyPair.fromAccountId(stellarAccountId)).cursor("now");

    final Listener listener = new Listener();

    effectsRequestBuilder.stream(listener);
  }

  public void waitForCredits(final long maxWait, final CreditMatcher... creditsExpected)
      throws Exception {
    waitForCredits(maxWait, Arrays.asList(creditsExpected));
  }

  public void waitForCredits(final long maxWait, final List<CreditMatcher> creditsExpected)
      throws Exception {
    logger.info("Waiting maximum {} milliseconds for credits {}.", maxWait, creditsExpected);

    final List<CreditMatcher> incompleteCredits = new LinkedList<>(creditsExpected);

    final long startTime = new Date().getTime();
    long waitedSoFar = 0;

    try (final Cleanup cleanup = new Cleanup()) {
      while (!incompleteCredits.isEmpty()) {
        final Credit credit = credits.poll(maxWait, TimeUnit.MILLISECONDS);

        final long now = new Date().getTime();
        waitedSoFar = now - startTime;

        if (credit != null) {
          final Optional<CreditMatcher> match = incompleteCredits.stream()
              .filter(incompleteCredit -> incompleteCredit.matches(credit)).findAny();
          match.ifPresent(incompleteCredits::remove);

          if (!match.isPresent())
            cleanup.addStep(() -> credits.put(credit));
        }


        if ((waitedSoFar > maxWait) && credits.isEmpty()) {
          logger.info("Waited {} milliseconds, and there are {} incomplete credits {} of the expected credits {}",
              waitedSoFar, incompleteCredits.size(), incompleteCredits, creditsExpected);
          return;
        }
      }

      logger.info("Waited {} milliseconds, and there are {} incomplete credits {} of the expected credits {}",
          waitedSoFar, incompleteCredits.size(), incompleteCredits, creditsExpected);
    }
  }

  public void waitForCreditsToAccumulate(final long maxWait, final CreditMatcher sumCreditExpected)
    throws Exception {
    logger.info("Waiting maximum {} milliseconds for creditMatcher accumulation to tenant {}.", maxWait, sumCreditExpected.toTenantId);
    BigDecimal missingBalance = sumCreditExpected.amount;

    final long startTime = new Date().getTime();
    long waitedSoFar = 0;

    try (final Cleanup cleanup = new Cleanup()) {
      while (missingBalance.compareTo(BigDecimal.ZERO) > 0)
      {
        final Credit credit = credits.poll(maxWait, TimeUnit.MILLISECONDS);

        final long now = new Date().getTime();
        waitedSoFar = now - startTime;

        if (credit != null) {
          final boolean matched = sumCreditExpected.assetCode.equals(credit.assetCode) &&
              sumCreditExpected.issuingTenantVaults.contains(credit.issuingTenantVault) &&
              sumCreditExpected.toTenantId.equals(credit.toTenantId);
          if (!matched)
            cleanup.addStep(() -> credits.put(credit));
          else
            missingBalance = missingBalance.subtract(credit.amount);
        }


        if ((waitedSoFar > maxWait) && credits.isEmpty()) {

          logger.info("Waited {} milliseconds for creditMatcher accumulation is up.  Missing balance is {} of {}",
              waitedSoFar, missingBalance, sumCreditExpected);
          return;
        }
      }

      logger.info("Waited {} milliseconds for creditMatcher accumulation is up.  Missing balance is {} of {}",
          waitedSoFar, missingBalance, sumCreditExpected);
    }
  }
}
