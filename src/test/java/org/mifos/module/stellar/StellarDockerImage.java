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

import com.github.dockerjava.api.ConflictException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.NotModifiedException;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.stellar.base.KeyPair;
import org.stellar.sdk.Server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class StellarDockerImage implements AutoCloseable {

  public static final String STELLAR_DOCKER_IMAGE = "stellar/stellar-core-horizon:latest";
  public static final String STELLAR_CONTAINER_NAME = "stellar-core-horizon";
  public static final int HORIZON_PORT = 8000;
  public static final String MASTER_ACCOUNT_PRIVATE_KEY =
      "SDHOAMBNLGCE2MV5ZKIVZAQD3VCLGP53P3OBSBI6UN5L5XZI5TKHFQL4";

  final DockerClient dockerClient;

  private Cleanup cleanup = new Cleanup();

  public StellarDockerImage() {
    dockerClient = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build();
    cleanup.addStep(dockerClient::close);

    dockerClient.pullImageCmd(STELLAR_DOCKER_IMAGE).exec(new PullImageResultCallback())
        .awaitSuccess();


    try {
      final CreateContainerResponse container =
          dockerClient.createContainerCmd(STELLAR_DOCKER_IMAGE)
              .withAttachStderr(false)
              .withAttachStdin(false)
              .withAttachStdout(false)
              .withPortBindings(new PortBinding(new Ports.Binding(), ExposedPort.tcp(HORIZON_PORT)))
              .withName(STELLAR_CONTAINER_NAME)
              .exec();
      cleanup.addStep(() -> {
        try {
          dockerClient.removeContainerCmd(container.getId()).exec();
        } catch (final DockerException e) { /* Do nothing.  Don't inhibit further cleanup.*/}
      });
    }
    catch (final ConflictException e)
    {
      //the container is already created.
    }

    try {
      dockerClient.startContainerCmd(STELLAR_CONTAINER_NAME).exec();

      cleanup.addStep(() -> {
        try {
          dockerClient.stopContainerCmd(STELLAR_CONTAINER_NAME).exec();
        } catch (final DockerException e) { /* Do nothing.  Don't inhibit further cleanup.*/}
      });
    }
    catch (final NotModifiedException e)
    {
      //the container is already running.
    }
  }

  @Override public void close() throws Exception {
    cleanup.cleanup();
  }

  private int portNumber() {
    final List<Container> containers = dockerClient.listContainersCmd().exec();
    final Container.Port[] ports = containers.stream()
        .filter(x -> Arrays.asList(x.getNames()).contains("/" + STELLAR_CONTAINER_NAME))
        .findFirst().get()
        .getPorts();

    return
        Arrays.asList(ports).stream()
            .filter(x -> x.getPrivatePort() == HORIZON_PORT).findFirst().get()
            .getPublicPort();
  }

  public String address() {
    return "http://localhost:" + portNumber();
  }

  interface Attempt
  {
    void again() throws IOException;
  }

  public void waitForStartupToComplete() throws IOException, InterruptedException {
    final Server server = new Server(address());

    final KeyPair masterAccountKeyPair
        = KeyPair.fromSecretSeed(MASTER_ACCOUNT_PRIVATE_KEY);

    retry(() -> server.accounts().account(masterAccountKeyPair), 120000);
  }

  private void retry(final Attempt attempt, final int howLong)
      throws IOException, InterruptedException {
    final long start = new Date().getTime();
    IOException lastE = null;
    long timeLeft = howLong;
    while(timeLeft > 0)
    {
      try {
        attempt.again();
        return;
      }
      catch (final IOException e) {
        lastE = e;
      }
      final long now = new Date().getTime();
      final long timePassed = now - start;
      timeLeft = howLong - timePassed;
      final long sleepTime = Math.max(3000, Math.min(timePassed, timeLeft));
      Thread.sleep(sleepTime);
    }

    if (lastE != null)
      throw lastE;

    throw new IOException("failed, and I don't know why");
  }
}
