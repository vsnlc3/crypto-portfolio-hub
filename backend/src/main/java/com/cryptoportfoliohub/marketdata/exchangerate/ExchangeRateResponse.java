package com.cryptoportfoliohub.marketdata.exchangerate;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Map;

public record ExchangeRateResponse(
        String result,
        @JsonProperty("error-type") String errorType,
        @JsonProperty("time_last_update_unix") Long timeLastUpdateUnix,
        @JsonProperty("base_code") String baseCode,
        @JsonProperty("conversion_rates") Map<String, BigDecimal> conversionRates) {
}
