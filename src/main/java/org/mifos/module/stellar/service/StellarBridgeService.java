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
import com.google.gson.Gson;
import org.mifos.module.stellar.federation.*;
import org.mifos.module.stellar.listener.MifosPaymentEvent;
import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.persistencedomain.MifosEventPersistency;
import org.mifos.module.stellar.persistencedomain.PaymentPersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepositoryDecorator;
import org.mifos.module.stellar.repository.MifosEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import org.stellar.base.KeyPair;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class StellarBridgeService implements ApplicationEventPublisherAware {
  private final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator;
  private ApplicationEventPublisher eventPublisher;
  private final MifosEventRepository mifosEventRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final Gson gson;
  private final StellarAddressResolver stellarAddressResolver;
  private final HorizonServerPaymentObserver horizonServerPaymentObserver;

  @Autowired
  public StellarBridgeService(
      final MifosEventRepository mifosEventRepository,
      final AccountBridgeRepositoryDecorator accountBridgeRepositoryDecorator,
      final HorizonServerUtilities horizonServerUtilities,
      final Gson gson,
      final StellarAddressResolver stellarAddressResolver,
      final HorizonServerPaymentObserver horizonServerPaymentObserver) {
    this.mifosEventRepository = mifosEventRepository;
    this.accountBridgeRepositoryDecorator = accountBridgeRepositoryDecorator;
    this.horizonServerUtilities = horizonServerUtilities;
    this.gson = gson;
    this.stellarAddressResolver = stellarAddressResolver;
    this.horizonServerPaymentObserver = horizonServerPaymentObserver;
  }

  @Override
  public void setApplicationEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * Create a bridge between a Mifos account and the stellar network.  This creates the Stellar
   * account and saves the association.
   *
   * @param mifosTenantId the id of the tenant we are setting this up for.
   * @param mifosToken a token to access mifos with.
   *
   * @throws InvalidConfigurationException
   * @throws StellarAccountCreationFailedException
   */
  public void createStellarBridgeConfig(
      final String mifosTenantId,
      final String mifosToken)
      throws InvalidConfigurationException, StellarAccountCreationFailedException
  {
    final KeyPair accountKeyPair = horizonServerUtilities.createAccount();

    horizonServerPaymentObserver.setupListeningForAccount(
        StellarAccountId.mainAccount(accountKeyPair.getAccountId()));

    this.accountBridgeRepositoryDecorator.save(
        mifosTenantId, mifosToken, accountKeyPair);
  }

  /**
   * Change the size of the trustline from a Mifos account to the specified Stellar account.  This
   * means that money issued by the stellar account can be transfered to the Mifos account.
   *
   * @param mifosTenantId The mifos tenant that wants to extend the credit line.
   * @param stellarAddressToTrust The stellar address (in form jed*stellar.org) that
   *                              is trusted.
   * @param assetCode The currency in which to extend credit.
   * @param maximumAmount the maximum amount of currency to trust from this source.
   */
  public void adjustTrustLine(
      final String mifosTenantId,
      final StellarAddress stellarAddressToTrust,
      final String assetCode,
      final BigDecimal maximumAmount)
      throws InvalidStellarAddressException,
      FederationFailedException, StellarTrustLineAdjustmentFailedException,
      InvalidConfigurationException
  {

    final StellarAccountId accountIdOfStellarAccountToTrust =
        getTopLevelStellarAccountId(stellarAddressToTrust);

    if (accountIdOfStellarAccountToTrust.equals(
        accountBridgeRepositoryDecorator.getStellarVaultAccountId(mifosTenantId)))
      throw StellarTrustLineAdjustmentFailedException.selfReferentialVaultTrustline(
          stellarAddressToTrust.toString());

    final char[] stellarAccountPrivateKey
        = accountBridgeRepositoryDecorator.getStellarAccountPrivateKey(mifosTenantId);


    horizonServerUtilities.setTrustLineSize(stellarAccountPrivateKey,
        accountIdOfStellarAccountToTrust, assetCode, maximumAmount);
  }

  private StellarAccountId getTopLevelStellarAccountId(StellarAddress stellarAddressToTrust)
      throws FederationFailedException, StellarTrustLineAdjustmentFailedException
  {
    final StellarAccountId accountIdOfStellarAccountToTrust =
        stellarAddressResolver.getAccountIdOfStellarAccount(stellarAddressToTrust);

    if (accountIdOfStellarAccountToTrust.getSubAccount().isPresent()) {
      throw StellarTrustLineAdjustmentFailedException
          .needTopLevelStellarAccount(stellarAddressToTrust.toString());
      //TODO: this function isn't just used by trustline adjustment.  Fix this.
    }
    return accountIdOfStellarAccountToTrust;
  }

  public boolean deleteAccountBridgeConfig(final String mifosTenantId)
  {
    return this.accountBridgeRepositoryDecorator.delete(mifosTenantId);

    //TODO: figure out what to do with the associated Stellar account before you delete its private key.
  }

  public void sendPaymentToStellar(
      final PaymentPersistency payment)
  {
    final Long eventId = this.saveEvent(payment);

    this.eventPublisher.publishEvent(new MifosPaymentEvent(this, eventId, payment));

    //TODO: still need to ensure replaying of unplayed events.
  }

  private Long saveEvent(final PaymentPersistency payment) {
    final MifosEventPersistency eventSource = new MifosEventPersistency();

    final String payload = gson.toJson(payment);
    eventSource.setPayload(payload);
    eventSource.setProcessed(Boolean.FALSE);
    final Date now = new Date();
    eventSource.setCreatedOn(now);
    eventSource.setLastModifiedOn(now);

    return this.mifosEventRepository.save(eventSource).getId();
  }

  public BigDecimal getBalance(final String mifosTenantId, final String assetCode)
  {
    final StellarAccountId stellarAccountId = accountBridgeRepositoryDecorator.getStellarAccountId(mifosTenantId);
    return this.horizonServerUtilities
        .getBalance(stellarAccountId, assetCode);
  }

  public BigDecimal getBalanceByIssuer(
      final String mifosTenantId,
      final String assetCode,
      final StellarAddress issuingStellarAddress)
      throws InvalidConfigurationException,
      FederationFailedException,
      StellarTrustLineAdjustmentFailedException
  {
    final StellarAccountId stellarAccountId
        = accountBridgeRepositoryDecorator.getStellarAccountId(mifosTenantId);

    final StellarAccountId issuingStellarAccountId =
        getTopLevelStellarAccountId(issuingStellarAddress);

    return this.horizonServerUtilities
        .getBalanceByIssuer(stellarAccountId, assetCode, issuingStellarAccountId);
  }

  public BigDecimal getInstallationAccountBalance(
      final String assetCode,
      final StellarAddress issuingStellarAddress) {

    final StellarAccountId issuingStellarAccountId =
        getTopLevelStellarAccountId(issuingStellarAddress);
    return horizonServerUtilities.getInstallationAccountBalance(
        assetCode, issuingStellarAccountId);
  }

  public BigDecimal adjustVaultIssuedAssets(
      final String mifosTenantId,
      final String assetCode,
      final BigDecimal amount)
      throws InvalidConfigurationException
  {
    final AccountBridgePersistency bridge
        = accountBridgeRepositoryDecorator.getBridge(mifosTenantId);

    if (bridge == null)
    {
      throw new IllegalArgumentException(mifosTenantId);
    }

    final StellarAccountId stellarVaultAccountId;
    final char[] stellarVaultAccountPrivateKey;

    if (bridge.getStellarVaultAccountId() == null) {
      final KeyPair vaultAccountKeyPair = createVaultAccount(bridge);

      if (amount.compareTo(BigDecimal.ZERO) <= 0)
        return BigDecimal.ZERO;

      stellarVaultAccountId = StellarAccountId.mainAccount(vaultAccountKeyPair.getAccountId());
      stellarVaultAccountPrivateKey = vaultAccountKeyPair.getSecretSeed();
    }
    else {
      stellarVaultAccountId = StellarAccountId.mainAccount(bridge.getStellarVaultAccountId());
      stellarVaultAccountPrivateKey = bridge.getStellarVaultAccountPrivateKey();
    }

    final StellarAccountId stellarAccountId
        = StellarAccountId.mainAccount(bridge.getStellarAccountId());

    final BigDecimal currentVaultIssuedAssets =
        horizonServerUtilities.currencyTrustSize(stellarAccountId, assetCode, stellarVaultAccountId);

    final BigDecimal adjustmentRequired = amount.subtract(currentVaultIssuedAssets);

    if (adjustmentRequired.compareTo(BigDecimal.ZERO) < 0)
    {
      final BigDecimal currentVaultIssuedAssetsHeldByTenant = horizonServerUtilities
          .getBalanceByIssuer(stellarAccountId, assetCode, stellarVaultAccountId);

      final BigDecimal adjustmentPossible
          = currentVaultIssuedAssetsHeldByTenant.min(adjustmentRequired.abs());

      final BigDecimal finalBalance = currentVaultIssuedAssets.subtract(adjustmentPossible);

      horizonServerUtilities.simplePay(
          stellarVaultAccountId,
          adjustmentPossible,
          assetCode,
          stellarVaultAccountId,
          bridge.getStellarAccountPrivateKey());

      horizonServerUtilities.setTrustLineSize(
          bridge.getStellarAccountPrivateKey(), stellarVaultAccountId, assetCode,
          finalBalance);

      return finalBalance;
    }
    else if (adjustmentRequired.compareTo(BigDecimal.ZERO) > 0)
    {
      horizonServerUtilities.setTrustLineSize(
          bridge.getStellarAccountPrivateKey(), stellarVaultAccountId, assetCode,
          amount);

      horizonServerUtilities.simplePay(
          stellarAccountId,
          adjustmentRequired,
          assetCode,
          stellarVaultAccountId,
          stellarVaultAccountPrivateKey);

      return amount;
    }
    else {
      return currentVaultIssuedAssets;
    }
  }

  public boolean tenantHasVault(
      final String mifosTenantId) {
    final StellarAccountId mifosTenantVaultAccountId
        = accountBridgeRepositoryDecorator.getStellarVaultAccountId(mifosTenantId);

    return (mifosTenantVaultAccountId != null);
  }

  public BigDecimal getVaultIssuedAssets(
      final String mifosTenantId,
      final String assetCode) {

    final StellarAccountId stellarAccountId =
        accountBridgeRepositoryDecorator.getStellarAccountId(mifosTenantId);

    final StellarAccountId stellarVaultAccountId
        = accountBridgeRepositoryDecorator.getStellarVaultAccountId(mifosTenantId);

    if (stellarVaultAccountId == null)
      return BigDecimal.ZERO;

    return horizonServerUtilities.currencyTrustSize(
        stellarAccountId, assetCode, stellarVaultAccountId);
  }

  private KeyPair createVaultAccount(final AccountBridgePersistency bridge) {
    final String stellarVaultAccountId = bridge.getStellarVaultAccountId();

    if (stellarVaultAccountId != null)
      throw new IllegalArgumentException("A vault account already exists for this mifos tenant.");

    final KeyPair newStellarVaultKeyPair = horizonServerUtilities.createAccount();
    accountBridgeRepositoryDecorator.addStellarVaultAccount(
        bridge.getMifosTenantId(), newStellarVaultKeyPair);

    return newStellarVaultKeyPair;
  }
}
