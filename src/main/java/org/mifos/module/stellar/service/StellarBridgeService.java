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
import org.mifos.module.stellar.federation.*;
import org.mifos.module.stellar.listener.MifosPaymentEvent;
import org.mifos.module.stellar.persistencedomain.AccountBridgePersistency;
import org.mifos.module.stellar.persistencedomain.MifosEventPersistency;
import org.mifos.module.stellar.repository.AccountBridgeRepository;
import org.mifos.module.stellar.repository.MifosEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import org.stellar.base.KeyPair;

import java.util.Date;

@Service
public class StellarBridgeService implements ApplicationEventPublisherAware {
  private ApplicationEventPublisher eventPublisher;
  private final MifosEventRepository mifosEventRepository;
  private final AccountBridgeRepository accountBridgeRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final LocalFederationService localfederationService;
  private final ExternalFederationService externalFederationService;

  @Autowired
  public StellarBridgeService(
      final MifosEventRepository mifosEventRepository,
      final AccountBridgeRepository accountBridgeRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final LocalFederationService localfederationService,
      final ExternalFederationService externalFederationService) {
    this.mifosEventRepository = mifosEventRepository;
    this.accountBridgeRepository = accountBridgeRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.localfederationService = localfederationService;
    this.externalFederationService = externalFederationService;
  }

  @Override
  public void setApplicationEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * Create a bridge between a Mifos account and a stellar account.  This creates the Stellar
   * account and saves the association.
   *
   * @param restApiKey for secure access
   * @param mifosTenantId the id of the tenant we are setting this up for.
   * @param mifosToken a token to access mifos with.
   *
   * @throws InvalidConfigurationException
   * @throws StellarAccountCreationFailedException
   */
  public void createStellarBridgeConfig(
      final String restApiKey,
      final String mifosTenantId,
      final String mifosToken)
      throws InvalidConfigurationException, StellarAccountCreationFailedException
  {
    final KeyPair accountKeyPair = horizonServerUtilities.createAccount();

    final AccountBridgePersistency accountBridge =
        new AccountBridgePersistency(
            restApiKey,
            mifosTenantId,
            mifosToken,
            accountKeyPair.getAccountId(),
            accountKeyPair.getSecretSeed());

    this.accountBridgeRepository.save(accountBridge);

    accountBridge.clearSensitiveData();
  }

  /**
   * Create a trustline from a Mifos account to the specified Stellar account.  This means
   * that the stellar account can transfer money to the Mifos account.
   *
   * @param mifosTenantId The mifos tenant that wants to extend trust.
   * @param stellarAddressToTrust The stellar address (in form jed*stellar.org) that
   *                              receives the trust.
   * @param currency The currency in which to extend trust.
   * @param maximumAmount the maximum amount of currency to trust from this source.
   */
  public void createTrustLine(
      final String mifosTenantId,
      final StellarAddress stellarAddressToTrust,
      final String currency,
      final long maximumAmount)
      throws InvalidStellarAddressException,
      FederationFailedException,
      StellarTrustLineCreationFailedException
  {
    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    final StellarAccountId accountIdOfStellarAccountToTrust
        = getAccountIdOfStellarAccount(stellarAddressToTrust);

    if (accountIdOfStellarAccountToTrust.getSubAccount().isPresent())
    {
      throw StellarTrustLineCreationFailedException.needTopLevelStellarAccount(
          stellarAddressToTrust.toString());
    }

    horizonServerUtilities.createTrustLine(
        accountBridge.getStellarAccountPrivateKey(),
        accountIdOfStellarAccountToTrust.getPublicKey(),
        currency,
        maximumAmount);

    accountBridge.clearSensitiveData();
  }

  private StellarAccountId getAccountIdOfStellarAccount(final StellarAddress stellarAddress) {
    if (localfederationService.handlesDomain(stellarAddress.getDomain())) {
      return localfederationService.getAccountId(stellarAddress);
    }
    else {
      return externalFederationService.getAccountId(stellarAddress);
    }
  }

  public boolean accountBridgeExistsForTenantId(final String mifosTenantId) {

    final AccountBridgePersistency accountBridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    return accountBridge != null;
  }

  public boolean deleteAccountBridgeConfig(final String mifosTenantId) {

    final AccountBridgePersistency bridge =
        this.accountBridgeRepository.findByMifosTenantId(mifosTenantId);

    if (bridge == null) {
      return false;
    }

    this.accountBridgeRepository.delete(bridge.getId());
    return true;
  }

  public void sendPaymentToStellar(
      final String mifosTenantId,
      final String payload)
  {
    final Long eventId = this.saveEvent(mifosTenantId, payload);

    this.eventPublisher.publishEvent(new MifosPaymentEvent(this, eventId, mifosTenantId, payload));
  }

  private Long saveEvent(final String mifosTenantId, final String payload) {
    final MifosEventPersistency eventSource = new MifosEventPersistency();
    eventSource.setTenantId(mifosTenantId);
    eventSource.setPayload(payload);
    eventSource.setProcessed(Boolean.FALSE);
    final Date now = new Date();
    eventSource.setCreatedOn(now);
    eventSource.setLastModifiedOn(now);

    return this.mifosEventRepository.save(eventSource).getId();
  }
}
