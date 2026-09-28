package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.provider.solana.config.SolanaProviderProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class SolanaProviderHttpTests {

    private static final String ADDRESS = "11111111111111111111111111111111";
    private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
    private static final String MINT = "So11111111111111111111111111111111111111112";
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void fetchesSolAndMergesClassicAndToken2022AccountsWithExactIntegerQuantities() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SolanaProviderProperties properties = new SolanaProviderProperties();
        properties.setRpcUrl("https://solana-fixture.invalid");
        expectRpc(server, "getBalance", """
                {"jsonrpc":"2.0","id":"crypto-portfolio-hub","result":{"context":{"slot":1},"value":18446744073709551615}}
                """);
        expectTokenAccounts(server, TOKEN_PROGRAM, "9007199254740993", 6);
        expectTokenAccounts(server, TOKEN_2022_PROGRAM, "1000", 6);
        SolanaBalanceProvider provider = new SolanaBalanceProvider(
                new SolanaRpcClient(builder.build(), properties), Clock.fixed(NOW, ZoneOffset.UTC));

        var balances = provider.fetchBalances(ADDRESS);

        assertThat(balances).hasSize(2);
        assertThat(balances.getFirst().assetKey()).isEqualTo("SOL");
        assertThat(balances.getFirst().category()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedAssetCategory.CRYPTO);
        assertThat(balances.getFirst().totalQuantity()).isEqualByComparingTo("18446744073.709551615");
        assertThat(balances.get(1).assetKey()).isEqualTo("SOLANA:" + MINT);
        assertThat(balances.get(1).symbol()).isNull();
        assertThat(balances.get(1).assetName()).isNull();
        assertThat(balances.get(1).totalQuantity()).isEqualByComparingTo("9007199254.741993");
        assertThat(balances.get(1).fetchedAt()).isEqualTo(NOW);
        server.verify();
    }

    @Test
    void readsSignaturePageAndParsedSwapWithoutFloatingPointOrIntermediateRouteLegs() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SolanaProviderProperties properties = heliusProperties();
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(request.getURI().getHost()).isEqualTo("mainnet.helius-rpc.com");
                    assertThat(UriComponentsBuilder.fromUri(request.getURI()).build(true)
                            .getQueryParams().getFirst("api-key")).isEqualTo("fixture-key");
                })
                .andExpect(jsonPath("$.method").value("getTransactionsForAddress"))
                .andExpect(jsonPath("$.params[1].filters.tokenAccounts").value("balanceChanged"))
                .andExpect(jsonPath("$.params[1].filters.status").value("any"))
                .andExpect(jsonPath("$.params[1].limit").value(1))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"data":[
                          {"signature":"fixture-signature","slot":10,"err":null,"blockTime":1790553600}
                        ],"paginationToken":"10:1"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/parsed-events/transactions");
                    assertThat(UriComponentsBuilder.fromUri(request.getURI()).build(true)
                            .getQueryParams().getFirst("api-key")).isEqualTo("fixture-key");
                })
                .andExpect(jsonPath("$.transactions[0]").value("fixture-signature"))
                .andExpect(jsonPath("$.commitment").value("finalized"))
                .andRespond(withSuccess("""
                        [{"signature":"fixture-signature","parserStatus":"OK","parsed":{
                          "slot":10,"blockTime":1790553600,"fee":5000,"feePayer":"11111111111111111111111111111111",
                          "transactionStatus":"OK",
                          "nativeTransfers":[{"fromUserAccount":"11111111111111111111111111111111","toUserAccount":"11111111111111111111111111111111","amount":900}],
                          "tokenTransfers":[
                            {"fromUserAccount":"11111111111111111111111111111111","toUserAccount":"counterparty","rawTokenAmount":9007199254740993,"decimals":6,"mint":"So11111111111111111111111111111111111111112"},
                            {"fromUserAccount":"route-intermediate","toUserAccount":"11111111111111111111111111111111","rawTokenAmount":1500000,"decimals":6,"mint":"USDCmint"}
                          ],
                          "summary":{"type":"swap","description":"ignored","parsedData":{"inner_swaps":[{"amount":"999999999"}]}}
                        }}]
                        """, MediaType.APPLICATION_JSON));
        SolanaActivityProvider provider = new SolanaActivityProvider(
                new HeliusClient(builder.build(), properties), new SolanaActivityNormalizer());

        var page = provider.fetchActivities(ADDRESS, null, 1);

        assertThat(page.nextCursor()).isEqualTo("10:1");
        assertThat(page.activities()).hasSize(1);
        var activity = page.activities().getFirst();
        assertThat(activity.providerEventId()).isEqualTo("fixture-signature");
        assertThat(activity.dedupKey()).isEqualTo("transaction:fixture-signature");
        assertThat(activity.eventType()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedActivityType.SWAP);
        assertThat(activity.originalEventType()).isEqualTo("swap");
        assertThat(activity.status()).isEqualTo("SUCCESS");
        assertThat(activity.occurredAt()).isEqualTo(Instant.ofEpochSecond(1790553600));
        assertThat(activity.legs()).hasSize(3);
        assertThat(activity.legs().get(0).direction()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedDirection.OUT);
        assertThat(activity.legs().get(0).quantity()).isEqualByComparingTo("9007199254.740993");
        assertThat(activity.legs().get(1).direction()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedDirection.IN);
        assertThat(activity.legs().get(1).quantity()).isEqualByComparingTo("1.5");
        assertThat(activity.legs().get(2).direction()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedDirection.FEE);
        assertThat(activity.legs().get(2).quantity()).isEqualByComparingTo("0.000005");
        server.verify();
    }

    @Test
    void failedTransactionDoesNotCreateTransferLegsButKeepsFeeAndTimestamp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SolanaProviderProperties properties = heliusProperties();
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"data":[{"signature":"failed-signature","err":{"InstructionError":[0,"Custom"]},"blockTime":1790553600}],"paginationToken":null}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/parsed-events/transactions"))
                .andRespond(withSuccess("""
                        [{"signature":"failed-signature","parserStatus":"OK","parsed":{
                          "blockTime":1790553600,"fee":5000,"feePayer":"11111111111111111111111111111111",
                          "transactionStatus":"ERROR","nativeTransfers":[],
                          "tokenTransfers":[{"fromUserAccount":"11111111111111111111111111111111","toUserAccount":"counterparty","rawTokenAmount":1000000,"decimals":6,"mint":"mint"}],
                          "summary":{"type":"transfer"}
                        }}]
                        """, MediaType.APPLICATION_JSON));
        SolanaActivityProvider provider = new SolanaActivityProvider(
                new HeliusClient(builder.build(), properties), new SolanaActivityNormalizer());

        var activity = provider.fetchActivities(ADDRESS, null, 1).activities().getFirst();

        assertThat(activity.status()).isEqualTo("FAILED");
        assertThat(activity.eventType()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedActivityType.OTHER);
        assertThat(activity.legs()).hasSize(1);
        assertThat(activity.legs().getFirst().direction()).isEqualTo(com.cryptoportfoliohub.provider.NormalizedDirection.FEE);
        server.verify();
    }

    @Test
    void mapsHeliusRateLimitAndDoesNotLeakApiKeyInProviderException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SolanaProviderProperties properties = heliusProperties();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("mainnet.helius-rpc.com"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        HeliusClient client = new HeliusClient(builder.build(), properties);

        assertThatThrownBy(() -> client.fetchSignaturePage(new SolanaAddress(ADDRESS), null, 10))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> {
                    var providerException = (ProviderException) exception;
                    assertThat(providerException.category()).isEqualTo(ProviderErrorCategory.RATE_LIMIT);
                    assertThat(providerException.getMessage()).doesNotContain("fixture-key");
                });
        server.verify();
    }

    @Test
    void forwardsHeliusPaginationCursorWithoutScanningHistoryAutomatically() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HeliusClient client = new HeliusClient(builder.build(), heliusProperties());
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/"))
                .andExpect(jsonPath("$.params[1].paginationToken").value("10:1"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"data":[],"paginationToken":null}}
                        """, MediaType.APPLICATION_JSON));

        var page = client.fetchSignaturePage(new SolanaAddress(ADDRESS), "10:1", 100);

        assertThat(page.signatures()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        server.verify();
    }

    @Test
    void mapsHeliusTimeoutWithoutExposingRequestUri() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("mainnet.helius-rpc.com"))
                .andRespond(withException(new java.net.SocketTimeoutException("fixture timeout")));
        HeliusClient client = new HeliusClient(builder.build(), heliusProperties());

        assertThatThrownBy(() -> client.fetchSignaturePage(new SolanaAddress(ADDRESS), null, 10))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.TIMEOUT));
        server.verify();
    }

    @Test
    void refusesToReturnAPartialBalanceWhenOneTokenProgramRequestFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SolanaProviderProperties properties = new SolanaProviderProperties();
        properties.setRpcUrl("https://solana-fixture.invalid");
        expectRpc(server, "getBalance", """
                {"jsonrpc":"2.0","id":"crypto-portfolio-hub","result":{"context":{"slot":1},"value":0}}
                """);
        server.expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST))
                .andExpect(jsonPath("$.method").value("getTokenAccountsByOwner"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"crypto-portfolio-hub","result":{"context":{"slot":1},"value":[]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        SolanaBalanceProvider provider = new SolanaBalanceProvider(
                new SolanaRpcClient(builder.build(), properties), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> provider.fetchBalances(ADDRESS))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.RATE_LIMIT));
        server.verify();
    }

    @Test
    void missingHeliusKeyLeavesBalanceProviderAvailableButActivityUnavailable() {
        SolanaProviderProperties properties = new SolanaProviderProperties();
        properties.setHeliusApiKey(" ");
        HeliusClient client = new HeliusClient(RestClient.create(), properties);

        assertThatThrownBy(() -> client.fetchSignaturePage(new SolanaAddress(ADDRESS), null, 10))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.UNAVAILABLE));
    }

    private static void expectRpc(MockRestServiceServer server, String method, String response) {
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                })
                .andExpect(jsonPath("$.method").value(method))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private static void expectTokenAccounts(MockRestServiceServer server, String program, String rawAmount, int decimals) {
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                })
                .andExpect(jsonPath("$.method").value("getTokenAccountsByOwner"))
                .andExpect(jsonPath("$.params[1].programId").value(program))
                .andExpect(jsonPath("$.params[2].encoding").value("jsonParsed"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"crypto-portfolio-hub","result":{"context":{"slot":2},"value":[
                          {"account":{"data":{"parsed":{"info":{"mint":"%s","owner":"%s","tokenAmount":{"amount":"%s","decimals":%d,"uiAmount":1.0}}}}}}
                        ]}}
                        """.formatted(MINT, ADDRESS, rawAmount, decimals), MediaType.APPLICATION_JSON));
    }

    private static SolanaProviderProperties heliusProperties() {
        SolanaProviderProperties properties = new SolanaProviderProperties();
        properties.setHeliusApiKey("fixture-key");
        return properties;
    }
}
