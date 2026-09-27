package com.cryptoportfoliohub.marketdata.coingecko;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.MarketDataHttpErrors;
import com.cryptoportfoliohub.marketdata.config.MarketDataProperties;
import java.net.URI;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Component;

@Component
public class CoinGeckoClient {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3/coins/markets";
    private final RestClient restClient;
    private final MarketDataProperties properties;
    private final Clock clock;

    public CoinGeckoClient(RestClient marketDataRestClient, MarketDataProperties properties, Clock clock) {
        this.restClient = marketDataRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    public Map<String, CoinGeckoPriceObservation> fetchPrices(Collection<String> coinIds) {
        Objects.requireNonNull(coinIds, "coinIds must not be null");
        if (coinIds.isEmpty()) {
            return Map.of();
        }
        if (properties.getCoinGeckoDemoApiKey().isBlank()) {
            throw new ProviderException(ProviderErrorCategory.UNAVAILABLE);
        }

        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("vs_currency", "usd")
                .queryParam("ids", String.join(",", coinIds))
                .queryParam("price_change_percentage", "24h")
                .queryParam("precision", "full")
                .build()
                .encode()
                .toUri();

        final CoinGeckoMarketRow[] rows;
        try {
            CoinGeckoMarketRow[] response = restClient.get()
                    .uri(uri)
                    .header("x-cg-demo-api-key", properties.getCoinGeckoDemoApiKey())
                    .retrieve()
                    .body(CoinGeckoMarketRow[].class);
            if (response == null) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
            rows = response;
        } catch (ProviderException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            throw new ProviderException(MarketDataHttpErrors.forStatus(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new ProviderException(MarketDataHttpErrors.forTransport(exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }

        Map<String, CoinGeckoPriceObservation> prices = new HashMap<>();
        for (CoinGeckoMarketRow row : rows) {
            if (row == null || row.id() == null || row.id().isBlank()) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
            if (row.currentPrice() == null || row.lastUpdated() == null) {
                continue;
            }
            if (row.lastUpdated().isAfter(clock.instant())) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
            CoinGeckoPriceObservation observation;
            try {
                observation = new CoinGeckoPriceObservation(row.currentPrice(), row.lastUpdated());
            } catch (IllegalArgumentException exception) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
            if (prices.putIfAbsent(row.id(), observation) != null) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
        }
        return Map.copyOf(prices);
    }

}
