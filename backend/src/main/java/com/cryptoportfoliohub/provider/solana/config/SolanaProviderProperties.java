package com.cryptoportfoliohub.provider.solana.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.solana")
public class SolanaProviderProperties {

    private String rpcUrl = "https://api.mainnet-beta.solana.com";
    private String heliusApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration activityInitialBackfillWindow = Duration.ofDays(90);
    private Duration activitySyncOverlap = Duration.ofHours(1);
    private int activityPageSize = 100;

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

    public Duration getActivityInitialBackfillWindow() {
        return activityInitialBackfillWindow;
    }

    public void setActivityInitialBackfillWindow(Duration activityInitialBackfillWindow) {
        if (activityInitialBackfillWindow == null || activityInitialBackfillWindow.isNegative()
                || activityInitialBackfillWindow.isZero()) {
            throw new IllegalArgumentException("Activity initial backfill window must be positive.");
        }
        this.activityInitialBackfillWindow = activityInitialBackfillWindow;
    }

    public Duration getActivitySyncOverlap() {
        return activitySyncOverlap;
    }

    public void setActivitySyncOverlap(Duration activitySyncOverlap) {
        if (activitySyncOverlap == null || activitySyncOverlap.isNegative()) {
            throw new IllegalArgumentException("Activity sync overlap cannot be negative.");
        }
        this.activitySyncOverlap = activitySyncOverlap;
    }

    public int getActivityPageSize() {
        return activityPageSize;
    }

    public void setActivityPageSize(int activityPageSize) {
        if (activityPageSize < 1 || activityPageSize > 1_000) {
            throw new IllegalArgumentException("Activity page size must be between 1 and 1000.");
        }
        this.activityPageSize = activityPageSize;
    }
}
