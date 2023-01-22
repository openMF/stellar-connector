/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.fineract.module.stellar.config;

import org.fineract.module.stellar.persistencedomain.AccountBridgeKeyPersistency;
import org.fineract.module.stellar.persistencedomain.AccountBridgePersistency;
import org.fineract.module.stellar.persistencedomain.FineractPaymentEventPersistency;
import org.fineract.module.stellar.persistencedomain.PaymentPersistency;
import org.fineract.module.stellar.persistencedomain.StellarAdjustOfferEventPersistency;
import org.fineract.module.stellar.persistencedomain.StellarCursorPersistency;
import org.fineract.module.stellar.persistencedomain.StellarPaymentEventPersistency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import shadow.com.google.gson.Gson;

@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = {
    "org.fineract.module.stellar.repository"
})
@EntityScan(basePackageClasses = {
    AccountBridgeKeyPersistency.class,
    AccountBridgePersistency.class,
    FineractPaymentEventPersistency.class,
    PaymentPersistency.class,
    StellarAdjustOfferEventPersistency.class,
    StellarCursorPersistency.class,
    StellarPaymentEventPersistency.class
})
@ComponentScan(basePackages = {
    "org.fineract.module.stellar.service",
    "org.fineract.module.stellar.listener",
    "org.fineract.module.stellar.controller",
    "org.fineract.module.stellar.repository",
    "org.fineract.module.stellar.federation",
    "org.fineract.module.stellar.horizonadapter",
    "org.fineract.module.stellar.fineractadapter"
})
@EnableAsync
@EnableScheduling
public class BridgeConfiguration {
  @Bean
  public SimpleApplicationEventMulticaster applicationEventMulticaster() {
    final SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
    multicaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
    return multicaster;
  }

  @Bean
  public Logger stellarBridgeLogger() {
    return LoggerFactory.getLogger("StellarBridge");
  }

  @Bean
  public Logger federationServerLogger() {
    return LoggerFactory.getLogger("FederationServer");
  }
}
