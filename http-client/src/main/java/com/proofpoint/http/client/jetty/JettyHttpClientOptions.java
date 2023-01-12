package com.proofpoint.http.client.jetty;

public class JettyHttpClientOptions {
    private final boolean disableCertificateVerification;

    JettyHttpClientOptions(boolean disableCertificateVerification) {
        this.disableCertificateVerification = disableCertificateVerification;
    }

    public boolean isDisableCertificateVerification() {
        return disableCertificateVerification;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean disableCertificateVerification = false;

        public Builder() {
        }

        public JettyHttpClientOptions build() {
            return new JettyHttpClientOptions(disableCertificateVerification);
        }

        public Builder setDisableCertificateVerification(boolean disableCertificateVerification) {
            this.disableCertificateVerification = disableCertificateVerification;
            return this;
        }
    }
}
