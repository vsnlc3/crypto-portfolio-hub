package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BitbankRestClientTests {

    private static final long REQUEST_TIME = 1_721_121_776_490L;
    private static final Instant NOW = Instant.ofEpochMilli(REQUEST_TIME);

    @Test
    void signsPrivateAssetsRequestAndReadsExactBalanceDecimals() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BitbankCredentials credentials = credentials("fixture-key", "hoge");
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/user/assets");
                    assertThat(request.getHeaders().getFirst("ACCESS-KEY")).isEqualTo("fixture-key");
                    assertThat(request.getHeaders().getFirst("ACCESS-REQUEST-TIME")).isEqualTo(Long.toString(REQUEST_TIME));
                    assertThat(request.getHeaders().getFirst("ACCESS-TIME-WINDOW")).isEqualTo("5000");
                    assertThat(request.getHeaders().getFirst("ACCESS-SIGNATURE")).isEqualTo(
                            BitbankRequestSigner.sign("hoge".getBytes(StandardCharsets.UTF_8), REQUEST_TIME,
                                    5_000, "/v1/user/assets"));
                })
                .andRespond(withSuccess(fixture("bitbank/assets.json"), MediaType.APPLICATION_JSON));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        List<BitbankDtos.Asset> assets = client.fetchAssets(credentials);

        assertThat(assets).hasSize(2);
        assertThat(assets.getFirst().asset()).isEqualTo("btc");
        assertThat(assets.getFirst().freeAmount()).isEqualByComparingTo("0.4");
        assertThat(assets.getFirst().onhandAmount()).isEqualByComparingTo("0.5");
        assertThat(assets.getFirst().lockedAmount()).isEqualByComparingTo("0.1");
        assertThat(assets.getFirst().withdrawingAmount()).isEqualByComparingTo("0.02");
        credentials.close();
        assertThat(credentials.toString()).doesNotContain("fixture-key", "hoge");
        server.verify();
    }

    @Test
    void sendsMillisecondHistoryWindowAndSignsTheExactQueryString() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BitbankCredentials credentials = credentials("fixture-key", "fixture-secret");
        server.expect(request -> {
                    assertThat(request.getURI().getRawQuery()).isEqualTo(
                            "count=1000&since=1790553600000&end=1790553605000&order=asc");
                    assertThat(request.getHeaders().getFirst("ACCESS-SIGNATURE")).isEqualTo(
                            BitbankRequestSigner.sign("fixture-secret".getBytes(StandardCharsets.UTF_8), REQUEST_TIME,
                                    5_000,
                                    "/v1/user/spot/trade_history?count=1000&since=1790553600000"
                                            + "&end=1790553605000&order=asc"));
                })
                .andRespond(withSuccess(fixture("bitbank/trades.json"), MediaType.APPLICATION_JSON));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        List<BitbankDtos.Trade> trades = client.fetchTrades(credentials, 1_790_553_600_000L, 1_790_553_605_000L);

        assertThat(trades).hasSize(3);
        assertThat(trades.getFirst().tradeId()).isEqualTo("12001");
        assertThat(trades.getFirst().amount()).isEqualByComparingTo("0.25");
        assertThat(trades.getFirst().executedAtMillis()).isEqualTo(1_790_553_600_123L);
        credentials.close();
        server.verify();
    }

    @Test
    void fetchesOfficialPairMetadataWithoutSendingCredentials() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/spot/pairs");
                    assertThat(request.getHeaders().getFirst("ACCESS-KEY")).isNull();
                    assertThat(request.getHeaders().getFirst("ACCESS-SIGNATURE")).isNull();
                })
                .andRespond(withSuccess(fixture("bitbank/spot-pairs.json"), MediaType.APPLICATION_JSON));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        List<BitbankDtos.SpotPair> pairs = client.fetchSpotPairs();

        assertThat(pairs).containsExactly(
                new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy"),
                new BitbankDtos.SpotPair("eth_btc", "eth", "btc"));
        server.verify();
    }

    @Test
    void readsCryptoDepositAndWithdrawalRowsWithoutExposingDestinationFields() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BitbankCredentials credentials = credentials("fixture-key", "fixture-secret");
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/user/deposit_history");
                    assertThat(request.getURI().getRawQuery()).isEqualTo("count=100&since=1790553600000&end=1790553605000");
                })
                .andRespond(withSuccess(fixture("bitbank/deposits.json"), MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/user/withdrawal_history");
                    assertThat(request.getURI().getRawQuery()).isEqualTo("count=100&since=1790553600000&end=1790553605000");
                })
                .andRespond(withSuccess(fixture("bitbank/withdrawals.json"), MediaType.APPLICATION_JSON));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        var deposits = client.fetchDeposits(credentials, null, 1_790_553_600_000L, 1_790_553_605_000L);
        var withdrawals = client.fetchWithdrawals(credentials, null, 1_790_553_600_000L, 1_790_553_605_000L);

        assertThat(deposits).hasSize(1);
        assertThat(deposits.getFirst().amount()).isEqualByComparingTo("0.125");
        assertThat(deposits.getFirst().foundAtMillis()).isEqualTo(1_790_553_603_123L);
        assertThat(withdrawals).hasSize(1);
        assertThat(withdrawals.getFirst().amount()).isEqualByComparingTo("1.5");
        assertThat(withdrawals.getFirst().fee()).isEqualByComparingTo("0.005");
        assertThat(withdrawals.getFirst().toString()).doesNotContain("sensitive-address");
        credentials.close();
        server.verify();
    }

    @Test
    void mapsProviderRateLimitEnvelopeWithoutLeakingTheResponse() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BitbankCredentials credentials = credentials("fixture-key", "fixture-secret");
        server.expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.GET))
                .andRespond(withSuccess(fixture("bitbank/rate-limit.json"), MediaType.APPLICATION_JSON));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> client.fetchAssets(credentials))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> {
                    ProviderException providerException = (ProviderException) exception;
                    assertThat(providerException.category()).isEqualTo(ProviderErrorCategory.RATE_LIMIT);
                    assertThat(providerException.getMessage()).doesNotContain("fixture-key", "fixture-secret", "10009");
                });
        credentials.close();
        server.verify();
    }

    @Test
    void mapsHttp429AndTimeoutToSafeCategories() {
        assertProviderHttpFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                ProviderErrorCategory.RATE_LIMIT);
        assertProviderTimeout();
    }

    private static void assertProviderHttpFailure(
            org.springframework.http.HttpStatus status, ProviderErrorCategory expectedCategory) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BitbankCredentials credentials = credentials("fixture-key", "fixture-secret");
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/user/assets"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(status));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> client.fetchAssets(credentials))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(expectedCategory));
        credentials.close();
        server.verify();
    }

    private static void assertProviderTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BitbankCredentials credentials = credentials("fixture-key", "fixture-secret");
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/user/assets"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withException(new java.net.SocketTimeoutException("fixture timeout")));
        BitbankRestClient client = new BitbankRestClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> client.fetchAssets(credentials))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.TIMEOUT));
        credentials.close();
        server.verify();
    }

    private static BitbankCredentials credentials(String apiKey, String apiSecret) {
        return new BitbankCredentials(apiKey.getBytes(StandardCharsets.UTF_8),
                apiSecret.getBytes(StandardCharsets.UTF_8));
    }

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource(name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
