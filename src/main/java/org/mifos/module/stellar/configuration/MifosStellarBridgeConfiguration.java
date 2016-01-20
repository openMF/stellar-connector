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
package org.mifos.module.stellar.configuration;

import com.google.gson.Gson;
import org.mifos.module.stellar.persistencedomain.MifosEventPersistency;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = {
    "org.mifos.module.stellar.repository"
})
@EntityScan(basePackageClasses = {
    MifosEventPersistency.class
})
@ComponentScan(basePackages = {
    "org.mifos.module.stellar.service",
    "org.mifos.module.stellar.listener",
    "org.mifos.module.stellar.controller",
    "org.mifos.module.stellar.repository",
    "org.mifos.module.stellar.federation"
})
public class MifosStellarBridgeConfiguration {
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
}
