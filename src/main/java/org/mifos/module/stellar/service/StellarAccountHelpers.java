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
package org.mifos.module.stellar.service;

import org.mifos.module.stellar.federation.StellarAccountId;
import org.stellar.base.Asset;
import org.stellar.base.AssetTypeNative;
import org.stellar.base.KeyPair;
import org.stellar.sdk.Account;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class StellarAccountHelpers {
  static BigDecimal getBalanceOfAsset(
      final Account sourceAccount,
      final Asset asset)
  {
    return getNumericAspectOfAsset(sourceAccount, asset,
        balance -> stellarBalanceToBigDecimal(balance.getBalance()));
  }

  static BigDecimal getNumericAspectOfAsset(
      final Account sourceAccount,
      final Asset asset,
      final Function<Account.Balance, BigDecimal> aspect)
  {
    final Optional<BigDecimal> balanceOfGivenAsset
        = Arrays.asList(sourceAccount.getBalances()).stream()
        .filter(balance -> getAssetOfBalance(balance).equals(asset))
        .map(aspect)
        .max(BigDecimal::compareTo);

    //Theoretically there shouldn't be more than one balance, but if this should turn out to be
    //incorrect, we return the largest one, rather than adding them together.

    return balanceOfGivenAsset.orElse(BigDecimal.ZERO);
  }

  static boolean balanceIsInAsset(
      final Account.Balance balance, final String assetCode)
  {
    if (balance.getAssetType() == null)
      return false;

    if (balance.getAssetCode() == null) {
      return assetCode.equals("XLM") && balance.getAssetType().equals("native");
    }

    return balance.getAssetCode().equals(assetCode);
  }

  static Asset getAssetOfBalance(
      final Account.Balance balance)
  {
    if (balance.getAssetCode() == null)
      return new AssetTypeNative();
    else
      return Asset.createNonNativeAsset(balance.getAssetCode(),
          KeyPair.fromAccountId(balance.getAssetIssuer()));
  }

  static BigDecimal stellarBalanceToBigDecimal(final String balance)
  {
    return BigDecimal.valueOf(Double.parseDouble(balance));
  }

  static String bigDecimalToStellarBalance(final BigDecimal balance)
  {
    return balance.toString();
  }


  static Asset getAsset(final String assetCode, final StellarAccountId targetIssuer) {
    return Asset.createNonNativeAsset(assetCode, KeyPair.fromAccountId(targetIssuer.getPublicKey()));
  }

  static BigDecimal getBalance(final Account tenantAccount, final String assetCode) {
    final Account.Balance[] balances = tenantAccount.getBalances();

    return Arrays.asList(balances).stream()
        .filter(balance -> balanceIsInAsset(balance, assetCode))
        .map(balance -> stellarBalanceToBigDecimal(balance.getBalance()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static Set<Asset> findAssetsWithBalance(
      final Account account,
      final BigDecimal amount,
      final String assetCode) {

    return findAssetsWithAspect(account, amount, assetCode,
        balance -> stellarBalanceToBigDecimal(balance.getBalance()));
  }

  static Set<Asset> findAssetsWithTrust(
      final Account account,
      final BigDecimal amount,
      final String assetCode) {

    return findAssetsWithAspect(account, amount, assetCode,
        StellarAccountHelpers::remainingTrustInBalance);
  }

  static BigDecimal remainingTrustInBalance(final Account.Balance balance)
  {
    return stellarBalanceToBigDecimal(balance.getLimit())
        .subtract(stellarBalanceToBigDecimal(balance.getBalance()));
  }

  private static Set<Asset> findAssetsWithAspect(
      final Account account,
      final BigDecimal amount,
      final String assetCode,
      final Function<Account.Balance, BigDecimal> numericAspect)
  {
    return Arrays.asList(account.getBalances()).stream()
        .filter(balance -> balanceIsInAsset(balance, assetCode))
        .filter(balance -> numericAspect.apply(balance).compareTo(amount) >= 0)
        .sorted((balance1, balance2) -> numericAspect.apply(balance1).compareTo
            (numericAspect.apply(balance2)))
        .map(StellarAccountHelpers::getAssetOfBalance)
        .collect(Collectors.toSet());
  }
}
