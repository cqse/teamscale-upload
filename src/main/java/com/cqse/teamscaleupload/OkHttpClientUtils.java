package com.cqse.teamscaleupload;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class OkHttpClientUtils {

    public static OkHttpClient createClient(boolean validateSsl) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        setSensibleTimeouts(builder);
        builder.followRedirects(false).followSslRedirects(false);

        if (!validateSsl) {
            disableSslValidation(builder);
        }
        return builder.build();
    }

    private static void disableSslValidation(OkHttpClient.Builder builder) {
        SSLSocketFactory sslSocketFactory;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{TrustAllCertificatesManager.INSTANCE}, new SecureRandom());
            sslSocketFactory = sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            System.err.println("Could not disable SSL certificate validation. Leaving it enabled");
            e.printStackTrace();
            return;
        }

        builder.sslSocketFactory(sslSocketFactory, TrustAllCertificatesManager.INSTANCE);
        builder.hostnameVerifier((hostName, session) -> true);
    }

    private static void setSensibleTimeouts(okhttp3.OkHttpClient.Builder builder) {
        builder.connectTimeout(60L, TimeUnit.SECONDS);
        builder.readTimeout(60L, TimeUnit.SECONDS);
        builder.writeTimeout(60L, TimeUnit.SECONDS);
    }

    private static class TrustAllCertificatesManager implements X509TrustManager {
        static final TrustAllCertificatesManager INSTANCE = new TrustAllCertificatesManager();

        public TrustAllCertificatesManager() {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }
    }

}
