package com.cryptoportfoliohub.marketdata.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.market-data")
public class MarketDataProperties {

    private String coinGeckoDemoApiKey = "";
    private String exchangeRateApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration priceCacheTtl = Duration.ofMinutes(10);
    private Duration priceFreshness = Duration.ofMinutes(15);
    private Duration fxFreshness = Duration.ofHours(72);
    private Duration fxMaximumRequestInterval = Duration.ofHours(24);

    public String getCoinGeckoDemoApiKey() {
        return coinGeckoDemoApiKey;
    }

    public void setCoinGeckoDemoApiKey(String coinGeckoDemoApiKey) {
        this.coinGeckoDemoApiKey = coinGeckoDemoApiKey == null ? "" : coinGeckoDemoApiKey.trim();
    }

    public String getExchangeRateApiKey() {
        return exchangeRateApiKey;
    }

    public void setExchangeRateApiKey(String exchangeRateApiKey) {
        this.exchangeRateApiKey = exchangeRateApiKey == null ? "" : exchangeRateApiKey.trim();
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

    public Duration getPriceCacheTtl() {
        return priceCacheTtl;
    }

    public void setPriceCacheTtl(Duration priceCacheTtl) {
        this.priceCacheTtl = priceCacheTtl;
    }

    public Duration getPriceFreshness() {
        return priceFreshness;
    }

    public void setPriceFreshness(Duration priceFreshness) {
        this.priceFreshness = priceFreshness;
    }

    public Duration getFxFreshness() {
        return fxFreshness;
    }

    public void setFxFreshness(Duration fxFreshness) {
        this.fxFreshness = fxFreshness;
    }

    public Duration getFxMaximumRequestInterval() {
        return fxMaximumRequestInterval;
    }

    public void setFxMaximumRequestInterval(Duration fxMaximumRequestInterval) {
        this.fxMaximumRequestInterval = fxMaximumRequestInterval;
    }
}
