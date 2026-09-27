package com.cryptoportfoliohub.marketdata.coingecko;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record CoinGeckoMarketRow(
        String id,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("last_updated") Instant lastUpdated) {
}
