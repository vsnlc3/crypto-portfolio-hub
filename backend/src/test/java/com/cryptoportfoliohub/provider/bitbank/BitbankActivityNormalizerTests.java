package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BitbankActivityNormalizerTests {

    private static final Instant FROM = Instant.ofEpochMilli(1_790_553_600_000L);
    private static final Instant TO = Instant.ofEpochMilli(1_790_553_605_000L);
    private final BitbankActivityNormalizer normalizer = new BitbankActivityNormalizer();

    @Test
    void normalizesBuySellDepositWithdrawalAndKeepsMarginTradesUnclassified() {
        List<NormalizedActivity> activities = normalizer.normalize(
                List.of(
                        trade("12001", "btc_jpy", "buy", null, "limit", "0.25", "100000",
                                "0.0001", "12.5", "12.5", 1_790_553_600_123L),
                        trade("12002", "eth_btc", "sell", null, "market", "2", "0.05",
                                "0", "0.001", "0.001", 1_790_553_601_123L),
                        trade("12003", "btc_jpy", "sell", "long", "limit", "0.1", "110000",
                                "0", "0", "0", 1_790_553_602_123L)),
                List.of(new BitbankDtos.Deposit("fixture-deposit-1", "btc", "BTC", new BigDecimal("0.125"),
                        "fixture-txid", "DONE", "NORMAL", 1_790_553_603_123L, 1_790_553_604_123L)),
                List.of(new BitbankDtos.Withdrawal("fixture-withdrawal-1", "eth", new BigDecimal("1.5"),
                        new BigDecimal("0.005"), "ETH", "fixture-withdrawal-txid", "DONE", 1_790_553_604_123L)),
                List.of(new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy"),
                        new BitbankDtos.SpotPair("eth_btc", "eth", "btc")),
                FROM,
                TO);

        assertThat(activities).hasSize(5);
        var buy = activities.get(0);
        assertThat(buy.providerEventId()).isEqualTo("12001");
        assertThat(buy.dedupKey()).isEqualTo("trade:12001");
        assertThat(buy.eventType()).isEqualTo(NormalizedActivityType.BUY);
        assertThat(buy.originalEventType()).isEqualTo("SPOT_TRADE:limit");
        assertThat(buy.occurredAt()).isEqualTo(Instant.ofEpochMilli(1_790_553_600_123L));
        assertThat(buy.legs()).extracting(NormalizedActivityLegView::from)
                .containsExactly(
                        new NormalizedActivityLegView(NormalizedDirection.OUT, "JPY", "0", "25000", "JPY"),
                        new NormalizedActivityLegView(NormalizedDirection.IN, "BTC", "1", "0.25", "BTC"),
                        new NormalizedActivityLegView(NormalizedDirection.FEE, "BTC", "2", "0.0001", "BTC"),
                        new NormalizedActivityLegView(NormalizedDirection.FEE, "JPY", "3", "12.5", "JPY"));

        var sell = activities.get(1);
        assertThat(sell.eventType()).isEqualTo(NormalizedActivityType.SELL);
        assertThat(sell.legs()).extracting(NormalizedActivityLegView::from)
                .containsExactly(
                        new NormalizedActivityLegView(NormalizedDirection.OUT, "ETH", "0", "2", "ETH"),
                        new NormalizedActivityLegView(NormalizedDirection.IN, "BTC", "1", "0.1", "BTC"),
                        new NormalizedActivityLegView(NormalizedDirection.FEE, "BTC", "2", "0.001", "BTC"));

        var margin = activities.get(2);
        assertThat(margin.eventType()).isEqualTo(NormalizedActivityType.OTHER);
        assertThat(margin.originalEventType()).isEqualTo("SPOT_TRADE:limit:POSITION_SIDE:long");
        assertThat(margin.legs()).isEmpty();

        var deposit = activities.get(3);
        assertThat(deposit.eventType()).isEqualTo(NormalizedActivityType.DEPOSIT);
        assertThat(deposit.status()).isEqualTo("DONE");
        assertThat(deposit.originalEventType()).isEqualTo("DEPOSIT:NORMAL");
        assertThat(deposit.legs()).extracting(NormalizedActivityLegView::from)
                .containsExactly(new NormalizedActivityLegView(NormalizedDirection.IN, "BTC", "0", "0.125", "BTC"));

        var withdrawal = activities.get(4);
        assertThat(withdrawal.eventType()).isEqualTo(NormalizedActivityType.WITHDRAW);
        assertThat(withdrawal.status()).isEqualTo("DONE");
        assertThat(withdrawal.legs()).extracting(NormalizedActivityLegView::from)
                .containsExactly(new NormalizedActivityLegView(NormalizedDirection.OUT, "ETH", "0", "1.5", "ETH"));
        assertThat(withdrawal.toString()).doesNotContain("fixture-withdrawal-txid", "sensitive-address");
    }

    @Test
    void doesNotDuplicateSpotQuoteFeeAndPreservesJpyDepositAsFiatLeg() {
        var activity = normalizer.normalize(
                List.of(trade("buy-1", "btc_jpy", "buy", null, "limit", "0.1", "100000",
                        "0", "5", "5", 1_790_553_600_123L)),
                List.of(new BitbankDtos.Deposit("jpy-deposit", "jpy", null, new BigDecimal("50000"), null,
                        "DONE", null, 1_790_553_600_123L, null)),
                List.of(),
                List.of(new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy")),
                FROM,
                TO);

        var buy = activity.stream().filter(value -> value.eventType() == NormalizedActivityType.BUY).findFirst().orElseThrow();
        var jpyDeposit = activity.stream().filter(value -> value.eventType() == NormalizedActivityType.DEPOSIT)
                .findFirst().orElseThrow();
        assertThat(buy.legs()).filteredOn(leg -> leg.direction() == NormalizedDirection.FEE)
                .hasSize(1);
        assertThat(buy.legs().get(2).quantity()).isEqualByComparingTo("5");
        assertThat(jpyDeposit.legs().getFirst().assetKey()).isEqualTo("JPY");
    }

    @Test
    void rejectsUnknownPairAndAmountsThatCannotFitTheDatabaseScale() {
        assertInvalidResponse(() -> normalizer.normalize(
                List.of(trade("trade-unknown", "unknown_pair", "buy", null, "limit", "1", "1",
                        "0", "0", "0", 1_790_553_600_123L)),
                List.of(), List.of(), List.of(), FROM, TO));
        assertInvalidResponse(() -> normalizer.normalize(
                List.of(trade("trade-scale", "btc_jpy", "buy", null, "limit", "0.0000000000000000001", "1",
                        "0", "0", "0", 1_790_553_600_123L)),
                List.of(), List.of(), List.of(new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy")), FROM, TO));
    }

    @Test
    void filtersRowsToRequestedWindowAndRejectsDuplicateEventIdentifiers() {
        var inWindow = trade("trade-1", "btc_jpy", "buy", null, "limit", "1", "2", "0", "0", "0",
                1_790_553_600_123L);
        var outOfWindow = trade("trade-old", "btc_jpy", "buy", null, "limit", "1", "2", "0", "0", "0",
                1_790_553_599_999L);
        assertThat(normalizer.normalize(List.of(outOfWindow, inWindow), List.of(), List.of(),
                List.of(new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy")), FROM, TO))
                .extracting(NormalizedActivity::providerEventId)
                .containsExactly("trade-1");

        assertInvalidResponse(() -> normalizer.normalize(List.of(inWindow, inWindow), List.of(), List.of(),
                List.of(new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy")), FROM, TO));
    }

    private static BitbankDtos.Trade trade(
            String id, String pair, String side, String positionSide, String type,
            String amount, String price, String baseFee, String quoteFee, String occurredQuoteFee, long occurredAt) {
        return new BitbankDtos.Trade(id, pair, side, positionSide, type,
                new BigDecimal(amount), new BigDecimal(price), new BigDecimal(baseFee),
                new BigDecimal(quoteFee), new BigDecimal(occurredQuoteFee), occurredAt);
    }

    private static void assertInvalidResponse(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> assertThat(((ProviderException) exception).category())
                        .isEqualTo(ProviderErrorCategory.INVALID_RESPONSE));
    }

    private record NormalizedActivityLegView(
            NormalizedDirection direction, String assetKey, String legIndex, String quantity, String originalCurrency) {
        private static NormalizedActivityLegView from(com.cryptoportfoliohub.provider.NormalizedActivityLeg leg) {
            return new NormalizedActivityLegView(leg.direction(), leg.assetKey(), Integer.toString(leg.legIndex()),
                    leg.quantity().toPlainString(), leg.originalCurrency());
        }
    }
}
