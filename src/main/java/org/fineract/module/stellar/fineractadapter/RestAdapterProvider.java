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
package org.fineract.module.stellar.fineractadapter;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;

@Component
public class RestAdapterProvider {

    
    
    private OkHttpClient clientOkHttp;

    public Retrofit get(final String endpoint) {
        final OkHttpClient okHttpClient = this.createClient();
        this.setClientOkHttp(okHttpClient);
        return new Retrofit.Builder().baseUrl(endpoint).client(okHttpClient).build();    
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

      SSLContext ctx = null;
      try {
        ctx = SSLContext.getInstance("TLS");

        ctx.init(null, certs, new SecureRandom());
      } catch (final java.security.GeneralSecurityException ignored) {
      }

      try {                
        if (ctx != null) {
          client.sslSocketFactory().createSocket();
        }
      } 
      catch (final Exception ignored) {
          ignored.printStackTrace();
      }

      return client;
    }
    
    /**
     * @return the clientOkHttp
     */
    public OkHttpClient getClientOkHttp() {
        return clientOkHttp;
    }

    /**
     * @param clientOkHttp the clientOkHttp to set
     */
    public void setClientOkHttp(OkHttpClient clientOkHttp) {
        this.clientOkHttp = clientOkHttp;
    }
}
