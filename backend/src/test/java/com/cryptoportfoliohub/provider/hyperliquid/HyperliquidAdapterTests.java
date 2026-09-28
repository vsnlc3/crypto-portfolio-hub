package com.cryptoportfoliohub.provider.hyperliquid;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillDirection;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillSide;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class HyperliquidAdapterTests {

    private static final String ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Instant FROM = NOW.minusSeconds(86_400);

    @Test
    void mapsAccountModesStrictlyAndKeepsDefaultUnsupported() {
        assertThat(HyperliquidAccountModes.map("disabled").mode()).isEqualTo(HyperliquidAccountMode.STANDARD);
        assertThat(HyperliquidAccountModes.map("unifiedAccount").mode())
                .isEqualTo(HyperliquidAccountMode.UNIFIED_ACCOUNT);
        assertThat(HyperliquidAccountModes.map("portfolioMargin").mode())
                .isEqualTo(HyperliquidAccountMode.PORTFOLIO_MARGIN);
        assertThat(HyperliquidAccountModes.map("default").mode()).isEqualTo(HyperliquidAccountMode.UNSUPPORTED);
        assertThat(HyperliquidAccountModes.map("dexAbstraction").mode())
                .isEqualTo(HyperliquidAccountMode.UNSUPPORTED);
        assertThat(HyperliquidAccountModes.map("futureMode").mode()).isEqualTo(HyperliquidAccountMode.UNKNOWN);
        assertThat(HyperliquidAccountModes.map(null).mode()).isEqualTo(HyperliquidAccountMode.UNKNOWN);
    }

    @Test
    void usesSpotTotalWithoutAddingHoldAndKeepsStandardAccountEquitySeparate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expect(server, "userAbstraction", "\"disabled\"");
        expect(server, "spotMeta", spotMetadata());
        expect(server, "spotClearinghouseState", """
                {"balances":[
                  {"coin":"USDC","token":0,"hold":"5","total":"100","entryNtl":"0"},
                  {"coin":"PURR","token":1,"hold":"0","total":"2","entryNtl":"0"}
                ]}
                """);
        expect(server, "perpDexs", "[null]");
        expect(server, "metaAndAssetCtxs", """
                [{"universe":[{"name":"BTC"},{"name":"HYPE"}],"collateralToken":0},
                 [{"markPx":"50000"},{"markPx":"10"}]]
                """);
        expect(server, "clearinghouseState", """
                {"assetPositions":[
                  {"position":{"coin":"BTC","szi":"0.01","entryPx":"49000","liquidationPx":null,
                    "leverage":{"type":"cross","value":5},"marginUsed":"20","unrealizedPnl":"10"}},
                  {"position":{"coin":"HYPE","szi":"-2","entryPx":"11","liquidationPx":"8",
                    "leverage":{"type":"isolated","value":3},"marginUsed":"4","unrealizedPnl":"-2"}}
                ],"marginSummary":{"accountValue":"110","totalMarginUsed":"24"}}
                """);
        HyperliquidAdapter adapter = adapter(builder);

        HyperliquidCurrentState state = adapter.fetchCurrentState(ADDRESS);

        assertThat(state.accountMode()).isEqualTo(HyperliquidAccountMode.STANDARD);
        assertThat(state.accountModeSupported()).isTrue();
        assertThat(state.spotBalances()).hasSize(2);
        assertThat(state.spotBalances().getFirst().assetKey())
                .isEqualTo("HYPERLIQUID:SPOT:0x00000000000000000000000000000000");
        assertThat(state.spotBalances().getFirst().totalQuantity()).isEqualByComparingTo("100");
        assertThat(state.spotBalances().getFirst().availableQuantity()).isEqualByComparingTo("95");
        assertThat(state.spotBalances().getFirst().lockedQuantity()).isEqualByComparingTo("5");
        var account = state.accountStates().get(1);
        assertThat(account.accountScope()).isEqualTo("PERP_DEX:DEFAULT");
        assertThat(account.accountCurrency()).isEqualTo("USDC");
        assertThat(account.accountEquity()).isEqualByComparingTo("110");
        assertThat(account.equityIncludesUnrealizedPnl()).isTrue();
        assertThat(state.positions()).hasSize(2);
        assertThat(state.positions().get(0).side()).hasToString("LONG");
        assertThat(state.positions().get(0).priceCurrency()).isEqualTo("USDT");
        assertThat(state.positions().get(0).marginCurrency()).isEqualTo("USDC");
        assertThat(state.positions().get(0).pnlCurrency()).isEqualTo("USDC");
        assertThat(state.positions().get(1).side()).hasToString("SHORT");
        assertThat(state.positions().get(1).priceCurrency()).isEqualTo("USDC");
        server.verify();
    }

    @Test
    void doesNotUsePerpAccountEquityForUnifiedOrUnknownModes() {
        HyperliquidCurrentState unified = currentStateForMode("\"unifiedAccount\"");
        assertThat(unified.accountMode()).isEqualTo(HyperliquidAccountMode.UNIFIED_ACCOUNT);
        assertThat(unified.accountStates().get(1).accountEquity()).isNull();
        assertThat(unified.accountStates().get(1).equityIncludesUnrealizedPnl()).isNull();

        HyperliquidCurrentState unsupported = currentStateForMode("\"default\"");
        assertThat(unsupported.accountMode()).isEqualTo(HyperliquidAccountMode.UNSUPPORTED);
        assertThat(unsupported.accountModeSupported()).isFalse();
        assertThat(unsupported.accountStates().get(1).accountEquity()).isNull();

        HyperliquidCurrentState portfolioMargin = currentStateForMode("\"portfolioMargin\"");
        assertThat(portfolioMargin.accountMode()).isEqualTo(HyperliquidAccountMode.PORTFOLIO_MARGIN);
        assertThat(portfolioMargin.accountStates().get(1).accountEquity()).isNull();
    }

    @Test
    void treatsModeLookupRateLimitAsUnknownAndDisablesNetWorthValuation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.hyperliquid.xyz"))
                .andExpect(jsonPath("$.type").value("userAbstraction"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        expect(server, "spotMeta", spotMetadata());
        expect(server, "spotClearinghouseState", "{\"balances\":[]}");
        expect(server, "perpDexs", "[null]");
        expect(server, "metaAndAssetCtxs", "[{\"universe\":[],\"collateralToken\":0},[]]");
        expect(server, "clearinghouseState", "{\"assetPositions\":[],\"marginSummary\":{\"accountValue\":\"110\"}}");

        HyperliquidCurrentState state = adapter(builder).fetchCurrentState(ADDRESS);

        assertThat(state.accountMode()).isEqualTo(HyperliquidAccountMode.UNKNOWN);
        assertThat(state.providerAbstractionMode()).isNull();
        assertThat(state.accountModeSupported()).isFalse();
        assertThat(state.accountStates().get(1).accountEquity()).isNull();
        server.verify();
    }

    @Test
    void keepsPerpDexEquityScopesSeparateAndLeavesUnknownHip3PriceCurrencyNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expect(server, "userAbstraction", "\"disabled\"");
        expect(server, "spotMeta", spotMetadata());
        expect(server, "spotClearinghouseState", "{\"balances\":[]}");
        expect(server, "perpDexs", "[null,{\"name\":\"xyz\"}]");
        expect(server, "metaAndAssetCtxs", "[{\"universe\":[],\"collateralToken\":0},[]]");
        expect(server, "clearinghouseState", "{\"assetPositions\":[],\"marginSummary\":{\"accountValue\":\"10\"}}");
        expect(server, "metaAndAssetCtxs", """
                [{"universe":[{"name":"xyz:XYZ100"}],"collateralToken":0},[{"markPx":"100"}]]
                """);
        expect(server, "clearinghouseState", """
                {"assetPositions":[{"position":{"coin":"xyz:XYZ100","szi":"-2","entryPx":"101",
                  "liquidationPx":"120","leverage":{"value":2},"marginUsed":"40","unrealizedPnl":"-2"}}],
                 "marginSummary":{"accountValue":"20"}}
                """);

        HyperliquidCurrentState state = adapter(builder).fetchCurrentState(ADDRESS);

        assertThat(state.accountStates()).extracting(value -> value.accountScope())
                .containsExactly("ACCOUNT", "PERP_DEX:DEFAULT", "PERP_DEX:xyz");
        assertThat(state.accountStates().get(1).accountEquity()).isEqualByComparingTo("10");
        assertThat(state.accountStates().get(2).accountEquity()).isEqualByComparingTo("20");
        assertThat(state.positions()).singleElement().satisfies(position -> {
            assertThat(position.positionKey()).isEqualTo("xyz:XYZ100");
            assertThat(position.priceCurrency()).isNull();
            assertThat(position.marginCurrency()).isEqualTo("USDC");
            assertThat(position.pnlCurrency()).isEqualTo("USDC");
        });
        server.verify();
    }

    @Test
    void translatesSpotAndPerpetualFillsWithoutTreatingPerpetualSizeAsAssetMovement() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.hyperliquid.xyz"))
                .andExpect(jsonPath("$.type").value("userFillsByTime"))
                .andExpect(jsonPath("$.aggregateByTime").value(false))
                .andExpect(jsonPath("$.startTime").value(FROM.toEpochMilli()))
                .andRespond(withSuccess("""
                        [
                          {"coin":"PURR/USDC","side":"B","sz":"3","px":"2","time":1790540000000,
                           "hash":"0xspot1","tid":1,"fee":"0.05","feeToken":"USDC"},
                          {"coin":"PURR/USDC","side":"A","sz":"1","px":"3","time":1790540000500,
                           "hash":"0xspot2","tid":4,"fee":"-0.01","feeToken":"USDC"},
                          {"coin":"BTC","side":"B","dir":"Open Long","sz":"0.01","px":"50000",
                           "startPosition":"0","closedPnl":"0","time":1790540001000,
                           "hash":"0xperp1","tid":2,"fee":"0.02","feeToken":"USDC"},
                          {"coin":"BTC","side":"A","dir":"Liquidation","sz":"0.01","px":"49000",
                           "startPosition":"0.01","closedPnl":"-10","time":1790540002000,
                           "hash":"0xperp2","tid":3,"fee":"-0.01","feeToken":"USDC"},
                          {"coin":"BTC","side":"B","dir":"Close Long","sz":"0.01","px":"49000",
                           "startPosition":"0.01","closedPnl":"-10","time":1790540002500,
                           "hash":"0xperp3","tid":5,"fee":"0","feeToken":"USDC"},
                          {"coin":"BTC","side":"A","dir":"Open Short","sz":"0.01","px":"49000",
                           "startPosition":"0","closedPnl":"0","time":1790540002600,
                           "hash":"0xperp4","tid":6,"fee":"0","feeToken":"USDC"},
                          {"coin":"BTC","side":"A","dir":"Close Short","sz":"0.01","px":"49000",
                           "startPosition":"-0.01","closedPnl":"-10","time":1790540002700,
                           "hash":"0xperp5","tid":7,"fee":"0","feeToken":"USDC"}
                        ]
                        """, MediaType.APPLICATION_JSON));
        expect(server, "userFunding", """
                [
                  {"delta":{"type":"funding","coin":"BTC","usdc":"0.5"},"hash":"0xfund1","time":1790540003000},
                  {"delta":{"type":"funding","coin":"BTC","usdc":"-0.3"},"hash":"0xfund2","time":1790540004000},
                  {"delta":{"type":"funding","coin":"BTC","usdc":"0"},"hash":"0xfund3","time":1790540005000}
                ]
                """);
        expect(server, "spotMeta", spotMetadata());
        expect(server, "perpDexs", "[null]");
        expect(server, "metaAndAssetCtxs", """
                [{"universe":[{"name":"BTC"}],"collateralToken":0},[{"markPx":"50000"}]]
                """);
        HyperliquidAdapter adapter = adapter(builder);

        var page = adapter.fetchActivities(ADDRESS, FROM, NOW);

        assertThat(page.limitedByProviderHistory()).isFalse();
        var spot = page.activities().stream().filter(a -> a.providerEventId().startsWith("0xspot1:")).findFirst().orElseThrow();
        assertThat(spot.eventType()).isEqualTo(NormalizedActivityType.BUY);
        assertThat(spot.legs()).extracting(leg -> leg.direction())
                .containsExactly(NormalizedDirection.IN, NormalizedDirection.OUT, NormalizedDirection.FEE);
        assertThat(spot.legs().get(0).quantity()).isEqualByComparingTo("3");
        assertThat(spot.legs().get(1).quantity()).isEqualByComparingTo("6");

        var sell = page.activities().stream().filter(a -> a.providerEventId().startsWith("0xspot2:")).findFirst().orElseThrow();
        assertThat(sell.eventType()).isEqualTo(NormalizedActivityType.SELL);
        assertThat(sell.legs()).extracting(leg -> leg.direction())
                .containsExactly(NormalizedDirection.OUT, NormalizedDirection.IN, NormalizedDirection.IN);
        assertThat(sell.legs().get(1).quantity()).isEqualByComparingTo("3");
        assertThat(sell.legs().get(2).quantity()).isEqualByComparingTo("0.01");

        var perp = page.activities().stream().filter(a -> a.eventType() == NormalizedActivityType.PERP).toList();
        assertThat(perp).hasSize(5);
        assertThat(perp.get(0).perpetualFillDetail().side()).isEqualTo(NormalizedPerpetualFillSide.BUY);
        assertThat(perp.get(0).perpetualFillDetail().direction())
                .isEqualTo(NormalizedPerpetualFillDirection.OPEN_LONG);
        assertThat(perp.get(0).perpetualFillDetail().priceCurrency()).isEqualTo("USDT");
        assertThat(perp.get(0).perpetualFillDetail().quantity()).isEqualByComparingTo("0.01");
        assertThat(perp.get(0).perpetualFillDetail().price()).isEqualByComparingTo("50000");
        assertThat(perp.get(0).perpetualFillDetail().startPosition()).isEqualByComparingTo("0");
        assertThat(perp.get(0).perpetualFillDetail().closedPnl()).isEqualByComparingTo("0");
        assertThat(perp.get(0).legs()).hasSize(1);
        assertThat(perp.get(0).legs().getFirst().direction()).isEqualTo(NormalizedDirection.FEE);
        assertThat(perp.get(0).legs().getFirst().quantity()).isEqualByComparingTo("0.02");
        assertThat(perp.get(1).perpetualFillDetail().direction()).isEqualTo(NormalizedPerpetualFillDirection.UNKNOWN);
        assertThat(perp.get(1).perpetualFillDetail().providerDirection()).isEqualTo("Liquidation");
        assertThat(perp.get(1).legs()).hasSize(1);
        assertThat(perp.get(1).legs().getFirst().direction()).isEqualTo(NormalizedDirection.IN);
        assertThat(perp.get(2).perpetualFillDetail().direction())
                .isEqualTo(NormalizedPerpetualFillDirection.CLOSE_LONG);
        assertThat(perp.get(2).legs()).isEmpty();
        assertThat(perp.get(3).perpetualFillDetail().direction())
                .isEqualTo(NormalizedPerpetualFillDirection.OPEN_SHORT);
        assertThat(perp.get(4).perpetualFillDetail().direction())
                .isEqualTo(NormalizedPerpetualFillDirection.CLOSE_SHORT);

        var funding = page.activities().stream().filter(a -> a.eventType() == NormalizedActivityType.FUNDING).toList();
        assertThat(funding).hasSize(3);
        assertThat(funding.get(0).legs().getFirst().direction()).isEqualTo(NormalizedDirection.IN);
        assertThat(funding.get(1).legs().getFirst().direction()).isEqualTo(NormalizedDirection.OUT);
        assertThat(funding.get(2).legs()).isEmpty();
        server.verify();
    }

    @Test
    void mapsRateLimitAndInvalidSpotIdentityWithoutLeakingRequestDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.hyperliquid.xyz"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        HyperliquidInfoClient client = new HyperliquidInfoClient(builder.build());

        assertThatThrownBy(() -> client.query(java.util.Map.of("type", "spotMeta")))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.RATE_LIMIT));
        server.verify();
    }

    @Test
    void mapsTimeoutToSafeProviderCategory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.hyperliquid.xyz"))
                .andRespond(withException(new java.net.SocketTimeoutException("fixture timeout")));
        HyperliquidInfoClient client = new HyperliquidInfoClient(builder.build());

        assertThatThrownBy(() -> client.query(java.util.Map.of("type", "spotMeta")))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.TIMEOUT));
        server.verify();
    }

    private static HyperliquidCurrentState currentStateForMode(String modeResponse) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expect(server, "userAbstraction", modeResponse);
        expect(server, "spotMeta", spotMetadata());
        expect(server, "spotClearinghouseState", "{\"balances\":[]}");
        expect(server, "perpDexs", "[null]");
        expect(server, "metaAndAssetCtxs", "[{\"universe\":[],\"collateralToken\":0},[]]");
        expect(server, "clearinghouseState", "{\"assetPositions\":[],\"marginSummary\":{\"accountValue\":\"110\"}}");
        HyperliquidCurrentState state = adapter(builder).fetchCurrentState(ADDRESS);
        server.verify();
        return state;
    }

    private static HyperliquidAdapter adapter(RestClient.Builder builder) {
        return new HyperliquidAdapter(new HyperliquidInfoClient(builder.build()), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void expect(MockRestServiceServer server, String type, String response) {
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.hyperliquid.xyz"))
                .andExpect(jsonPath("$.type").value(type))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private static String spotMetadata() {
        return """
                {"tokens":[
                  {"name":"USDC","fullName":"USD Coin","index":0,
                   "tokenId":"0x00000000000000000000000000000000"},
                  {"name":"PURR","fullName":"Purr","index":1,
                   "tokenId":"0x11111111111111111111111111111111"}
                ],"universe":[{"name":"PURR/USDC","tokens":[1,0],"index":0}]}
                """;
    }
}
