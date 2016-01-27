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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.io.IOException;

public class StellarDockerImage implements AutoCloseable {

  public static final String STELLAR_DOCKER_IMAGE = "stellar/stellar-core-horizon:latest";

  final DockerClient dockerClient;
  final String stellarDockerContainerId;

  public StellarDockerImage() {
    dockerClient = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build();

    dockerClient.pullImageCmd(STELLAR_DOCKER_IMAGE).exec(new PullImageResultCallback()).awaitSuccess();

    final CreateContainerResponse container = dockerClient
        .createContainerCmd(STELLAR_DOCKER_IMAGE)
        .withAttachStderr(false)
        .withAttachStdin(false)
        .withAttachStdout(false)
        .exec();

    stellarDockerContainerId = container.getId();
    dockerClient.startContainerCmd(stellarDockerContainerId).exec();
  }

  @Override public void close() throws IOException {
    dockerClient.stopContainerCmd(stellarDockerContainerId).exec();
    dockerClient.removeContainerCmd(stellarDockerContainerId).exec();
    dockerClient.close();
  }
}
