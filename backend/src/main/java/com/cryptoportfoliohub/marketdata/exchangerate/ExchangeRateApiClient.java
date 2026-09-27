package com.cryptoportfoliohub.marketdata.exchangerate;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.MarketDataHttpErrors;
import com.cryptoportfoliohub.marketdata.config.MarketDataProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ExchangeRateApiClient {

    private static final String BASE_URL = "https://v6.exchangerate-api.com";
    private final RestClient restClient;
    private final MarketDataProperties properties;
    private final Clock clock;

    public ExchangeRateApiClient(RestClient marketDataRestClient, MarketDataProperties properties, Clock clock) {
        this.restClient = marketDataRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    public FxObservation fetchUsdToJpy() {
        String apiKey = properties.getExchangeRateApiKey();
        if (apiKey.isBlank()) {
            throw new ProviderException(ProviderErrorCategory.UNAVAILABLE);
        }

        final URI uri;
        try {
            uri = UriComponentsBuilder.fromUriString(BASE_URL)
                    .pathSegment("v6", apiKey, "latest", "USD")
                    .build()
                    .encode()
                    .toUri();
        } catch (RuntimeException exception) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }

        final ExchangeRateResponse response;
        try {
            ExchangeRateResponse body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(ExchangeRateResponse.class);
            if (body == null) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
            response = body;
        } catch (ProviderException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            throw new ProviderException(MarketDataHttpErrors.forStatus(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new ProviderException(MarketDataHttpErrors.forTransport(exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }

        if (!"success".equals(response.result())) {
            throw new ProviderException(categoryForApiError(response.errorType()));
        }
        if (!"USD".equals(response.baseCode()) || response.timeLastUpdateUnix() == null
                || response.timeLastUpdateUnix() <= 0
                || response.conversionRates() == null) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        BigDecimal rate = response.conversionRates().get("JPY");
        if (rate == null || rate.signum() <= 0) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }

        Instant evaluatedAt;
        try {
            evaluatedAt = Instant.ofEpochSecond(response.timeLastUpdateUnix());
        } catch (RuntimeException exception) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        if (evaluatedAt.isAfter(clock.instant())) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        return new FxObservation(rate, evaluatedAt);
    }

    private static ProviderErrorCategory categoryForApiError(String errorType) {
        if ("quota-reached".equals(errorType)) {
            return ProviderErrorCategory.RATE_LIMIT;
        }
        if ("invalid-key".equals(errorType) || "inactive-account".equals(errorType)) {
            return ProviderErrorCategory.AUTHENTICATION;
        }
        return ProviderErrorCategory.INVALID_RESPONSE;
    }

}
