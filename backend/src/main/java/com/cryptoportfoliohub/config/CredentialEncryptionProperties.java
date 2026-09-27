package com.cryptoportfoliohub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.credential-encryption")
public class CredentialEncryptionProperties {

    private String keyBase64 = "";
    private int currentKeyVersion = 1;

    public String getKeyBase64() {
        return keyBase64;
    }

    public void setKeyBase64(String keyBase64) {
        this.keyBase64 = keyBase64;
    }

    public int getCurrentKeyVersion() {
        return currentKeyVersion;
    }

    public void setCurrentKeyVersion(int currentKeyVersion) {
        this.currentKeyVersion = currentKeyVersion;
    }

    @Override
    public String toString() {
        return "CredentialEncryptionProperties[keyBase64=[REDACTED], currentKeyVersion="
                + currentKeyVersion + "]";
    }
}
