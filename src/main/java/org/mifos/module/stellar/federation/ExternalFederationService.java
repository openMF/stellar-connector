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
package org.mifos.module.stellar.federation;

import com.google.common.net.InternetDomainName;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.moandjiezana.toml.Toml;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.http.GET;
import retrofit.http.Query;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Service
public class ExternalFederationService {

  private final MockableExternalCalls mockableExternalCalls;

  public ExternalFederationService()
  {
    mockableExternalCalls = new MockableExternalCalls();
  }

  ExternalFederationService(final MockableExternalCalls mockableExternalCalls)
  {
    this.mockableExternalCalls = mockableExternalCalls;
  }

  /**
   * Based on the stellar address, finds the stellar account id.  Resolves the domain, and calls
   * the federation service to do so.  This only returns an account id if the memo type is id or
   * there is no memo type.
   *
   * @param stellarAddress The stellar address for which to return a stellar account id.
   * @return The corresponding stellar account id.
   *
   * @throws FederationFailedException for the following cases:
   * * domain server not reachable,
   * * stellar.toml not parseable for federation server,
   * * federation server not reachable,
   * * federation server response does not match expected format.
   * * memo type is not id.
   */
  public StellarAccountId getAccountId(final StellarAddress stellarAddress)
      throws FederationFailedException
  {
    final URL federationServerAddress = getFederationServerAddress(stellarAddress.getDomain());
    return getAccountIdFromFederationServer(federationServerAddress, stellarAddress);
  }

  private URL getFederationServerAddress(final InternetDomainName addressDomain)
      throws FederationFailedException
  {
    final URL tomlUrl;
    try {
      tomlUrl = new URL("https", addressDomain.toString(), "/.well-known/stellar.toml");
    } catch (final MalformedURLException e) {
      throw FederationFailedException.domainDoesNotReferToValidFederationServer(
          addressDomain.toString());
    }

    final InputStream tomlStream;
    try {
      tomlStream = mockableExternalCalls.getStreamFromUrl(tomlUrl);
    } catch (IOException e) {
      throw FederationFailedException
          .domainDoesNotReferToValidFederationServer(addressDomain.toString());
    }

    final Toml stellarToml = mockableExternalCalls.getTomlParser(tomlStream);

    final String federationServerValue = stellarToml.getString("FEDERATION_SERVER");

    try {
      return new URL(federationServerValue);
    } catch (final MalformedURLException e) {
      throw FederationFailedException.domainDoesNotReferToValidFederationServer(addressDomain.toString());
    }
  }

  private StellarAccountId getAccountIdFromFederationServer(
      final URL federationServerAddress,
      final StellarAddress stellarAddress)
      throws FederationFailedException
  {
    final FederationService federationService = mockableExternalCalls.getExternalFederationService(
            federationServerAddress.toExternalForm());

    final FederationResponse response;
    try {
      response = federationService.getAccountIdFromAddress("name", stellarAddress.toString());
    }
    catch (final FederationApiGetFailed e)
    {
      if (e.getStatus() == HttpStatus.NOT_FOUND)
      {
        throw FederationFailedException.addressNameNotFound(stellarAddress.toString());
      }
      else {
        throw FederationFailedException
            .domainDoesNotReferToValidFederationServer(stellarAddress.getDomain().toString());
      }
    }

    return convertFederationResponseToStellarAddress(response);
  }

  private StellarAccountId convertFederationResponseToStellarAddress(
      final FederationResponse response)
  {
    if (response.getMemoType().equalsIgnoreCase("text"))
    {
      return StellarAccountId.subAccount(response.getAccountId(), response.getMemo());
    }
    else if (response.getMemoType().isEmpty())
    {
      return StellarAccountId.mainAccount(response.getAccountId());
    }
    else
    {
      throw FederationFailedException.addressRequiresUnsupportedMemoType(response.getMemoType());
    }
  }

  interface FederationService {
    @GET("/federation")
    FederationResponse getAccountIdFromAddress(
        @Query("type") final String type,
        @Query("q") final String nameToLookUp);
  }

  static class FederationApiGetFailed extends RuntimeException {
    private final HttpStatus status;

    FederationApiGetFailed(final HttpStatus status) {
      this.status = status;
    }

    HttpStatus getStatus() {
      return status;
    }
  }

  /**
   * This class creates a seem for the purpose of making the rest of the code testable.
   * This code is not covered by unit tests because it has external dependencies.
   */
  static class MockableExternalCalls
  {
    InputStream getStreamFromUrl(final URL tomlUrl)
        throws IOException
    {
      //TODO: This code ignores the certificate instead of verifying  it.  That makes man-in-the-
      //TODO: middle attacks possible.
      final OkHttpClient client = createClient();
      final Request request = new Request.Builder().url(tomlUrl).build();
      final Response response = client.newCall(request).execute();
      return response.body().byteStream();
    }

    Toml getTomlParser(final InputStream tomlStream) {
      return new Toml().parse(tomlStream);
    }

    FederationService getExternalFederationService(final String endpoint) {
      //TODO: This code ignores the certificate instead of verifying  it.  That makes man-in-the-
      //TODO: middle attacks possible.

      final RestAdapter restAdapter =
          new RestAdapter.Builder()
              .setEndpoint(endpoint)
              .setClient(new OkClient(createClient()))
              .setErrorHandler((final RetrofitError cause) ->
                  new FederationApiGetFailed(HttpStatus.valueOf(cause.getResponse().getStatus())))
              .build();

      return restAdapter.create(FederationService.class);
    }

    private OkHttpClient createClient() {

      final OkHttpClient client = new OkHttpClient();

      final TrustManager[] certs = new TrustManager[]{new X509TrustManager() {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain,
            final String authType) throws CertificateException {
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain,
            final String authType) throws CertificateException {
        }
      }};

      final SSLContext ctx;
      try {
        ctx = SSLContext.getInstance("TLS");
        ctx.init(null, certs, new SecureRandom());
      } catch (final java.security.GeneralSecurityException ex) {
        //This is only good enough because this is throw away code.
        throw FederationFailedException.domainDoesNotReferToValidFederationServer("unexpected error");
      }

      try {
        client.setHostnameVerifier((String, SSLSession) -> (true));
        client.setSslSocketFactory(ctx.getSocketFactory());
      } catch (final Exception e) {
        //This is only good enough because this is throw away code.
        throw FederationFailedException.domainDoesNotReferToValidFederationServer("unexpected error");
      }

      return client;
    }
  }
}
