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
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;

public class MifosXDocker implements AutoCloseable {

  public static final String MIFOSX_DOCKER_IMAGE = "mifosx-test:latest";
  public static final String MIFOSX_CONTAINER_NAME = "mifosx-test";
  public static final int PORT = 8443;

  final DockerClient dockerClient;

  private Cleanup cleanup = new Cleanup();

  public MifosXDocker() {
    dockerClient = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build();
    cleanup.addStep(dockerClient::close);

    /*List<SearchItem> x = dockerClient.searchImagesCmd(MIFOSX_DOCKER_IMAGE).exec();
    if (x.isEmpty()) {
      dockerClient.pullImageCmd(MIFOSX_DOCKER_IMAGE).exec(new PullImageResultCallback())
          .awaitSuccess();
    }*/


    try {
      final CreateContainerResponse container =
          dockerClient.createContainerCmd(MIFOSX_DOCKER_IMAGE)
              .withAttachStderr(false)
              .withAttachStdin(false)
              .withAttachStdout(false)
              .withPortBindings(new PortBinding(new Ports.Binding(), ExposedPort.tcp(PORT)))
              .withName(MIFOSX_CONTAINER_NAME)
              .exec();
      cleanup.addStep(() -> {
        try {
          dockerClient.removeContainerCmd(container.getId()).exec();
        }catch (final DockerException e) { /* Do nothing.  Don't inhibit further cleanup.*/}
      });
    }
    catch (final ConflictException e)
    {
      //the container is already created.
    }

    try {
      dockerClient.startContainerCmd(MIFOSX_CONTAINER_NAME).exec();

      cleanup.addStep(() -> {
        try {
          dockerClient.stopContainerCmd(MIFOSX_CONTAINER_NAME).exec();
        } catch (final DockerException e) { /* Do nothing.  Don't inhibit further cleanup.*/}
      });
    }
    catch (final NotModifiedException e)
    {
      //the container is already running.
    }

    /* final String execId = dockerClient.execCreateCmd(MIFOSX_CONTAINER_NAME).withCmd(
        "mysql -u root -p" + DB_PASSWORD + "; \\\n"
            + "      create database `mifosplatform-tenants`; \\\n"
            + "      create database `mifostenant-default`;").exec().getId();

    dockerClient.execStartCmd(MIFOSX_CONTAINER_NAME).withExecId(execId).exec();*/

  }

  @Override public void close() throws Exception {
    cleanup.cleanup();
  }

  private int portNumber() {
    final List<Container> containers = dockerClient.listContainersCmd().exec();
    final Container.Port[] ports = containers.stream()
        .filter(x -> Arrays.asList(x.getNames()).contains("/" + MIFOSX_CONTAINER_NAME))
        .findFirst().get()
        .getPorts();

    return
        Arrays.asList(ports).stream()
            .filter(x -> x.getPrivatePort() == PORT).findFirst().get()
            .getPublicPort();
  }

  public String address() {
    return "https://localhost:" + portNumber();
  }

  public void waitForStartupToComplete() throws Exception {

    retry(this::testIfStartupIsComplete, 120000);
  }

  private void testIfStartupIsComplete() {
    RestAssured.useRelaxedHTTPSValidation();

    Response response = given().baseUri(address()).header("Fineract-Platform-TenantId", "default")
        .queryParam("username", "mifos").queryParam("password", "password").post("/fineract-provider/api/v1/authentication");
    response
        .then().assertThat().statusCode(HttpStatus.OK.value());

    final String defaultTenantToken = response.jsonPath().getString("base64EncodedAuthenticationKey");
  }

  private void retry(final Runnable attempt, final int howLong)
      throws Exception {
    final long start = new Date().getTime();
    Exception lastE = null;
    long timeLeft = howLong;
    while(timeLeft > 0)
    {
      try {
        attempt.run();
        return;
      }
      catch (final Exception e) {
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

    throw new Exception("failed, and I don't know why");
  }
}
