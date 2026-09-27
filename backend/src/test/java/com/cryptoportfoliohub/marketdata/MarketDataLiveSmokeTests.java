package com.cryptoportfoliohub.marketdata;

import com.cryptoportfoliohub.marketdata.coingecko.CoinGeckoClient;
import com.cryptoportfoliohub.marketdata.config.MarketDataProperties;
import com.cryptoportfoliohub.marketdata.exchangerate.ExchangeRateApiClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfSystemProperty(named = "market-data.live-smoke", matches = "true")
class MarketDataLiveSmokeTests {

    @Test
    void readsCoinGeckoPriceAndUsdJpyFromLiveProvidersWithoutExposingCredentials() {
        String coinGeckoKey = System.getenv("COINGECKO_DEMO_API_KEY");
        String exchangeRateKey = System.getenv("EXCHANGERATE_API_KEY");
        assumeTrue(coinGeckoKey != null && !coinGeckoKey.isBlank(), "CoinGecko credential is not configured.");
        assumeTrue(exchangeRateKey != null && !exchangeRateKey.isBlank(), "ExchangeRate-API credential is not configured.");

        MarketDataProperties properties = new MarketDataProperties();
        properties.setCoinGeckoDemoApiKey(coinGeckoKey);
        properties.setExchangeRateApiKey(exchangeRateKey);
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        Clock clock = Clock.systemUTC();

        var prices = new CoinGeckoClient(restClient, properties, clock).fetchPrices(List.of("bitcoin", "ethereum"));
        var usdJpy = new ExchangeRateApiClient(restClient, properties, clock).fetchUsdToJpy();

        assertThat(prices).containsKey("bitcoin");
        assertThat(prices.get("bitcoin").amount()).isPositive();
        assertThat(prices.get("bitcoin").evaluatedAt()).isNotNull();
        assertThat(usdJpy.rate()).isPositive();
        assertThat(usdJpy.evaluatedAt()).isNotNull();
    }
}
