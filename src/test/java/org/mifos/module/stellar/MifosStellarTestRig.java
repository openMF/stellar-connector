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
package org.mifos.module.stellar;


import com.jayway.restassured.RestAssured;

public class MifosStellarTestRig implements AutoCloseable {
  private final String mifosAddress;

  private final Cleanup suiteCleanup = new Cleanup();

  public MifosStellarTestRig() throws Exception {
    final StellarDockerImage stellarDockerImage = new StellarDockerImage();
    suiteCleanup.addStep(stellarDockerImage::close);

    final MifosXDocker mifosXDocker = new MifosXDocker();
    suiteCleanup.addStep(mifosXDocker::close);

    stellarDockerImage.waitForStartupToComplete();
    mifosXDocker.waitForStartupToComplete();

    System.setProperty("stellar.horizon-address", stellarDockerImage.address());
    mifosAddress = mifosXDocker.address();
  }

  @Override public void close() throws Exception {
    suiteCleanup.cleanup();
  }

  public String getMifosAddress() {
    return mifosAddress;
  }
}
