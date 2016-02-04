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
import org.mifos.module.stellar.persistencedomain.MifosEventPersistency;
import org.mifos.module.stellar.persistencedomain.PaymentPersistency;
import org.mifos.module.stellar.repository.AccountBridgeEncodedRepository;
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
  private final StellarAddressResolver stellarAddressResolver;
  private final AccountBridgeEncodedRepository accountBridgeEncodedRepository;
  private ApplicationEventPublisher eventPublisher;
  private final MifosEventRepository mifosEventRepository;
  private final HorizonServerUtilities horizonServerUtilities;
  private final Gson gson;

  @Autowired
  public StellarBridgeService(
      final MifosEventRepository mifosEventRepository,
      final AccountBridgeEncodedRepository accountBridgeEncodedRepository,
      final HorizonServerUtilities horizonServerUtilities,
      final Gson gson,
      final StellarAddressResolver stellarAddressResolver) {
    this.mifosEventRepository = mifosEventRepository;
    this.accountBridgeEncodedRepository = accountBridgeEncodedRepository;
    this.horizonServerUtilities = horizonServerUtilities;
    this.gson = gson;
    this.stellarAddressResolver = stellarAddressResolver;
  }

  @Override
  public void setApplicationEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * Create a bridge between a Mifos account and a stellar account.  This creates the Stellar
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

    this.accountBridgeEncodedRepository.save(
        mifosTenantId, mifosToken, accountKeyPair.getAccountId(), accountKeyPair.getSecretSeed());
  }

  /**
   * Create a trustline from a Mifos account to the specified Stellar account.  This means
   * that the stellar account can transfer money to the Mifos account.
   *
   * @param mifosTenantId The mifos tenant that wants to extend the credit line.
   * @param stellarAddressToTrust The stellar address (in form jed*stellar.org) that
   *                              receives the credit line.
   * @param currency The currency in which to extend credit.
   * @param maximumAmount the maximum amount of currency to trust from this source.
   */
  public void createCreditLine(
      final String mifosTenantId,
      final StellarAddress stellarAddressToTrust,
      final String currency,
      final BigDecimal maximumAmount)
      throws InvalidStellarAddressException,
      FederationFailedException, StellarCreditLineCreationFailedException,
      InvalidConfigurationException
  {

    final StellarAccountId accountIdOfStellarAccountToTrust =
        getTopLevelStellarAccountId(stellarAddressToTrust);

    final char[] decodedPrivateKey = accountBridgeEncodedRepository.getPrivateKey(mifosTenantId);

    horizonServerUtilities.setCreditLineSize(decodedPrivateKey,
        accountIdOfStellarAccountToTrust.getPublicKey(), currency, maximumAmount);
  }

  public void deleteCreditLine(
      final String mifosTenantId,
      final StellarAddress stellarAddress,
      final String assetCode) {

    final StellarAccountId accountIdOfStellarAccountToTrust =
        getTopLevelStellarAccountId(stellarAddress);

    final char[] decodedPrivateKey = accountBridgeEncodedRepository.getPrivateKey(mifosTenantId);

    horizonServerUtilities.minimizeCreditLine(decodedPrivateKey,
        accountIdOfStellarAccountToTrust.getPublicKey(), assetCode);
  }

  private StellarAccountId getTopLevelStellarAccountId(StellarAddress stellarAddressToTrust) {
    final StellarAccountId accountIdOfStellarAccountToTrust =
        stellarAddressResolver.getAccountIdOfStellarAccount(stellarAddressToTrust);

    if (accountIdOfStellarAccountToTrust.getSubAccount().isPresent()) {
      throw StellarCreditLineCreationFailedException
          .needTopLevelStellarAccount(stellarAddressToTrust.toString());
    }
    return accountIdOfStellarAccountToTrust;
  }

  public boolean deleteAccountBridgeConfig(final String mifosTenantId)
  {
    return this.accountBridgeEncodedRepository.delete(mifosTenantId);

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
    final char[] decodedPrivateKey = accountBridgeEncodedRepository.getPrivateKey(mifosTenantId);
    return this.horizonServerUtilities
        .getBalance(decodedPrivateKey, assetCode);
  }

  public BigDecimal getInstallationAccountBalance(
      final String assetCode) {
    return horizonServerUtilities.getInstallationAccountBalance(assetCode);
  }
}
