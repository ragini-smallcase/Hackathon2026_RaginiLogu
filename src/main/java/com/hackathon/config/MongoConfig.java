package com.hackathon.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoSslCustomizer() {
        return builder -> {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }}, new java.security.SecureRandom());

                builder.applyToSslSettings(ssl -> ssl
                        .enabled(true)
                        .invalidHostNameAllowed(true)
                        .context(sslContext));
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure MongoDB SSL", e);
            }
        };
    }
}
