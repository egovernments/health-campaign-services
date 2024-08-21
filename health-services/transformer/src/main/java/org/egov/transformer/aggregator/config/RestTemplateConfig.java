package org.egov.transformer.aggregator.config;


import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class RestTemplateConfig {

  /**
   * ES8 cluster default configuration with security enabled forces the use of https for
   * communication to the ES cluster. This function is used to accept the self signed certificates
   * from the ES8 cluster so SSLCertificateException is not thrown. The ideal way to solve this is
   * to import the self signed certificates into the JKS.
   */
  public static void trustSelfSignedSSL() {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      X509TrustManager tm = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] xcs, String string) {
        }

        public void checkServerTrusted(X509Certificate[] xcs, String string) {
        }

        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }
      };
      ctx.init(null, new TrustManager[]{tm}, null);
      SSLContext.setDefault(ctx);

      // Disable hostname verification
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, sslSession) -> Boolean.TRUE);
    } catch (Exception ex) {
      log.error("Error while trusting self-signed certificate ::: {}", ex.getMessage(), ex);
    }
  }


  @Bean("customRestTemplate")
  public RestTemplate restTemplate() {
    trustSelfSignedSSL();
    return new RestTemplate();
  }
}
