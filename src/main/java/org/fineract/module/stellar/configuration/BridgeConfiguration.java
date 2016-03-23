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
package org.fineract.module.stellar.configuration;

import com.google.gson.Gson;
import org.fineract.module.stellar.persistencedomain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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
  public Gson gson()
  {
    return new Gson();
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
