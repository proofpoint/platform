package com.proofpoint.http.client.jetty;

public class JettyHttpClientOptions {
    private final boolean enableCertificateVerification;

    JettyHttpClientOptions(boolean enableCertificateVerification) {
        this.enableCertificateVerification = enableCertificateVerification;
    }

    public boolean isEnableCertificateVerification() {
        return enableCertificateVerification;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enableCertificateVerification = true;

        public Builder() {
        }

        public JettyHttpClientOptions build() {
            return new JettyHttpClientOptions(enableCertificateVerification);
        }

        public Builder setEnableCertificateVerification(boolean enableCertificateVerification) {
            this.enableCertificateVerification = enableCertificateVerification;
            return this;
        }
    }
}
