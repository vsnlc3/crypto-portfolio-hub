package com.cryptoportfoliohub.provider.solana.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.solana")
public class SolanaProviderProperties {

    private String rpcUrl = "https://api.mainnet-beta.solana.com";
    private String heliusApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl == null ? "" : rpcUrl.trim();
    }

    public String getHeliusApiKey() {
        return heliusApiKey;
    }

    public void setHeliusApiKey(String heliusApiKey) {
        this.heliusApiKey = heliusApiKey == null ? "" : heliusApiKey.trim();
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
