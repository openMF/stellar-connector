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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;

import java.util.Collections;

public class StellarDockerImage implements AutoCloseable {

  static class StellarDockerImageException extends Exception
  {
    StellarDockerImageException(final String msg) { super(msg);}
  }

  public static final String STELLAR_DOCKER_IMAGE = "stellar/stellar-core-horizon:latest";

  final DockerClient docker;
  final String stellarContainerId;

  public StellarDockerImage() throws StellarDockerImageException {
    try {
      docker = DefaultDockerClient.fromEnv().build();
    } catch (final DockerCertificateException e) {
      throw new StellarDockerImageException("docker environment creation failed in certification.");
    }

    try {
      docker.pull(STELLAR_DOCKER_IMAGE);
    } catch (final DockerException e) {
      throw new StellarDockerImageException("pulling stellar docker image failed.");
    } catch (final InterruptedException e) {
      throw new StellarDockerImageException("pulling stellar docker image was interrupted.");
    }

    final PortBinding portBinding = PortBinding.randomPort("0.0.0.0");
    try {
      final HostConfig stellarDockerHostConfig = HostConfig.builder()
          .portBindings(Collections.singletonMap("8000", Collections.singletonList(portBinding)))
          .build();
      final ContainerConfig stellarContainerConfig = ContainerConfig.builder()
          .hostConfig(stellarDockerHostConfig)
          .attachStdin(false)
          .attachStdout(false)
          .attachStderr(false)
          .build();

      final ContainerCreation stellarContainerCreation = docker.createContainer(stellarContainerConfig);
      stellarContainerId = stellarContainerCreation.id();
    } catch (final DockerException e) {
      throw new StellarDockerImageException("creating stellar docker container failed.");
    } catch (final InterruptedException e) {
      throw new StellarDockerImageException("creating stellar docker container was interrupted.");
    }

    try {
      docker.startContainer(stellarContainerId);
    } catch (final DockerException e) {
      throw new StellarDockerImageException("starting stellar docker image failed");
    } catch (final InterruptedException e) {
      throw new StellarDockerImageException("starting stellar docker image was interrupted.");
    }
  }

  @Override public void close() throws StellarDockerImageException{
    try {
      docker.killContainer(stellarContainerId);
    } catch (final DockerException e) {
      throw new StellarDockerImageException("killing stellar docker container failed.");
    } catch (final InterruptedException e) {
      throw new StellarDockerImageException("killing stellar docker container was interrupted.");
    }

    try {
      docker.removeContainer(stellarContainerId);
    } catch (final DockerException e) {
      throw new StellarDockerImageException("removing stellar docker container failed.");
    } catch (final InterruptedException e) {
      throw new StellarDockerImageException("removing stellar docker container was interrupted.");
    }
    docker.close();
  }
}
