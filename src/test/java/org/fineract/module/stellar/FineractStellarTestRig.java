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


public class FineractStellarTestRig implements AutoCloseable {
  private final String mifosAddress;

  private final Cleanup suiteCleanup = new Cleanup();

  public FineractStellarTestRig() throws Exception {
    final StellarDocker stellarDocker = new StellarDocker();
    suiteCleanup.addStep(stellarDocker::close);

    final FineractDocker fineractDocker = new FineractDocker();
    suiteCleanup.addStep(fineractDocker::close);

    stellarDocker.waitForStartupToComplete();
    fineractDocker.waitForStartupToComplete();

    System.setProperty("stellar.horizon-address", stellarDocker.address());
    mifosAddress = fineractDocker.address();
  }

  @Override public void close() throws Exception {
    suiteCleanup.cleanup();
  }

  public String getMifosAddress() {
    return mifosAddress;
  }
}
