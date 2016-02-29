/**
 * Copyright 2014 Markus Geiss
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
package org.mifos.module.stellar.fineractadapter;

import com.squareup.okhttp.OkHttpClient;
import org.springframework.stereotype.Component;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Component
public class RestAdapterProvider {

  public RestAdapter get(final String endpoint) {

    final OkHttpClient okHttpClient = this.createClient();

    return new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(new OkClient(okHttpClient))
        .build();
  }

  public OkHttpClient createClient() {

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

    SSLContext ctx = null;
    try {
      ctx = SSLContext.getInstance("TLS");
      ctx.init(null, certs, new SecureRandom());
    } catch (final java.security.GeneralSecurityException ignored) {
    }

    try {
      client.setHostnameVerifier((hostname, session) -> true);
      if (ctx != null) {
        client.setSslSocketFactory(ctx.getSocketFactory());
      }
    } catch (final Exception ignored) {
    }

    return client;
  }
}
