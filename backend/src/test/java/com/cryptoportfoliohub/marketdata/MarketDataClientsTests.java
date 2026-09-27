package com.cryptoportfoliohub.marketdata;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.coingecko.CoinGeckoClient;
import com.cryptoportfoliohub.marketdata.config.MarketDataProperties;
import com.cryptoportfoliohub.marketdata.exchangerate.ExchangeRateApiClient;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;

class MarketDataClientsTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void requestsCoinGeckoIdsTogetherWithRequiredCurrencyAndDemoHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties properties = new MarketDataProperties();
        properties.setCoinGeckoDemoApiKey("fixture-only-key");
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                    assertThat(request.getURI().getPath()).isEqualTo("/api/v3/coins/markets");
                    var query = UriComponentsBuilder.fromUri(request.getURI()).build(true).getQueryParams();
                    assertThat(query.getFirst("vs_currency")).isEqualTo("usd");
                    assertThat(query.getFirst("ids")).isEqualTo("bitcoin,ethereum");
                    assertThat(query.getFirst("price_change_percentage")).isEqualTo("24h");
                    assertThat(query.getFirst("precision")).isEqualTo("full");
                    assertThat(request.getHeaders().getFirst("x-cg-demo-api-key")).isEqualTo("fixture-only-key");
                })
                .andRespond(withSuccess("""
                        [
                          {"id":"bitcoin","current_price":64000.123456789,"price_change_percentage_24h":1.2345,"last_updated":"2026-09-27T11:59:00Z"},
                          {"id":"ethereum","current_price":2510.75,"price_change_percentage_24h":null,"last_updated":"2026-09-27T11:58:00Z"}
                        ]
                        """, MediaType.APPLICATION_JSON));
        CoinGeckoClient client = new CoinGeckoClient(builder.build(), properties, CLOCK);

        var prices = client.fetchPrices(java.util.List.of("bitcoin", "ethereum"));

        assertThat(prices).containsKeys("bitcoin", "ethereum");
        assertThat(prices.get("bitcoin").amount()).isEqualByComparingTo("64000.123456789");
        assertThat(prices.get("bitcoin").change24hPercentage()).contains(new BigDecimal("1.2345"));
        assertThat(prices.get("bitcoin").evaluatedAt()).isEqualTo(Instant.parse("2026-09-27T11:59:00Z"));
        assertThat(prices.get("ethereum").change24hPercentage()).isEmpty();
        server.verify();
    }

    @Test
    void classifiesCoinGeckoRateLimitWithoutIncludingRequestDataInError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties properties = new MarketDataProperties();
        properties.setCoinGeckoDemoApiKey("fixture-only-key");
        server.expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        CoinGeckoClient client = new CoinGeckoClient(builder.build(), properties, CLOCK);

        assertThatThrownBy(() -> client.fetchPrices(java.util.List.of("bitcoin")))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> {
                    ProviderException providerException = (ProviderException) exception;
                    assertThat(providerException.category()).isEqualTo(ProviderErrorCategory.RATE_LIMIT);
                    assertThat(providerException.getMessage()).doesNotContain("fixture-only-key");
                });
        server.verify();
    }

    @Test
    void classifiesAuthenticationPermissionTimeoutAndServerErrors() {
        assertCoinGeckoStatus(org.springframework.http.HttpStatus.UNAUTHORIZED, ProviderErrorCategory.AUTHENTICATION);
        assertCoinGeckoStatus(org.springframework.http.HttpStatus.FORBIDDEN, ProviderErrorCategory.PERMISSION);
        assertCoinGeckoStatus(org.springframework.http.HttpStatus.REQUEST_TIMEOUT, ProviderErrorCategory.TIMEOUT);
        assertCoinGeckoStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, ProviderErrorCategory.UNAVAILABLE);

        assertThat(MarketDataHttpErrors.forTransport(new org.springframework.web.client.ResourceAccessException(
                "request failed", new SocketTimeoutException())))
                .isEqualTo(ProviderErrorCategory.TIMEOUT);
    }

    @Test
    void readsExchangeRateUsdBaseJpyRateAndProviderUpdateTime() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties properties = new MarketDataProperties();
        properties.setExchangeRateApiKey("fixture-only-key");
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                    assertThat(request.getURI().getPath()).isEqualTo("/v6/fixture-only-key/latest/USD");
                })
                .andRespond(withSuccess("""
                        {
                          "result":"success",
                          "time_last_update_unix":1790510340,
                          "base_code":"USD",
                          "conversion_rates":{"USD":1,"JPY":149.987654321}
                        }
                        """, MediaType.APPLICATION_JSON));
        ExchangeRateApiClient client = new ExchangeRateApiClient(builder.build(), properties, CLOCK);

        var observation = client.fetchUsdToJpy();

        assertThat(observation.rate()).isEqualByComparingTo("149.987654321");
        assertThat(observation.evaluatedAt()).isEqualTo(Instant.ofEpochSecond(1790510340));
        server.verify();
    }

    @Test
    void classifiesExchangeRateApiQuotaAndInvalidKeyErrors() {
        assertExchangeRateError("quota-reached", ProviderErrorCategory.RATE_LIMIT);
        assertExchangeRateError("invalid-key", ProviderErrorCategory.AUTHENTICATION);
    }

    @Test
    void rejectsMalformedExchangeRatePayloadInsteadOfReturningZero() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties properties = new MarketDataProperties();
        properties.setExchangeRateApiKey("fixture-only-key");
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"result":"success","time_last_update_unix":1790510340,"base_code":"USD","conversion_rates":{"JPY":0}}
                """, MediaType.APPLICATION_JSON));
        ExchangeRateApiClient client = new ExchangeRateApiClient(builder.build(), properties, CLOCK);

        assertThatThrownBy(client::fetchUsdToJpy)
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.INVALID_RESPONSE));
        server.verify();
    }

    private static void assertExchangeRateError(String errorType, ProviderErrorCategory expectedCategory) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties properties = new MarketDataProperties();
        properties.setExchangeRateApiKey("fixture-only-key");
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"result":"error","error-type":"%s"}
                """.formatted(errorType), MediaType.APPLICATION_JSON));
        ExchangeRateApiClient client = new ExchangeRateApiClient(builder.build(), properties, CLOCK);

        assertThatThrownBy(client::fetchUsdToJpy)
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category()).isEqualTo(expectedCategory));
        server.verify();
    }

    private static void assertCoinGeckoStatus(
            org.springframework.http.HttpStatus status,
            ProviderErrorCategory expectedCategory) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties properties = new MarketDataProperties();
        properties.setCoinGeckoDemoApiKey("fixture-only-key");
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(status));
        CoinGeckoClient client = new CoinGeckoClient(builder.build(), properties, CLOCK);

        assertThatThrownBy(() -> client.fetchPrices(java.util.List.of("bitcoin")))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category()).isEqualTo(expectedCategory));
        server.verify();
    }
}
