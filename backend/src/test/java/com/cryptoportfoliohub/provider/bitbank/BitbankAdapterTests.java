package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.provider.NormalizedActivityType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitbankAdapterTests {

    private static final UUID CONNECTION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void usesOnhandAsTotalAndPreservesAvailableAndLockedWithoutAddingWithdrawingSubset() {
        BitbankCredentialLoader credentialLoader = mock(BitbankCredentialLoader.class);
        BitbankRestClient restClient = mock(BitbankRestClient.class);
        when(credentialLoader.load(CONNECTION_ID, USER_ID)).thenReturn(credentials());
        when(restClient.fetchAssets(any(BitbankCredentials.class))).thenReturn(List.of(
                new BitbankDtos.Asset("btc", new BigDecimal("0.4"), new BigDecimal("0.5"),
                        new BigDecimal("0.1"), new BigDecimal("0.02"), 8),
                new BitbankDtos.Asset("jpy", new BigDecimal("10000"), new BigDecimal("12000"),
                        new BigDecimal("2000"), BigDecimal.ZERO, 0)));
        BitbankAdapter adapter = new BitbankAdapter(credentialLoader, restClient,
                new BitbankActivityNormalizer(), Clock.fixed(NOW, ZoneOffset.UTC));

        var balances = adapter.fetchBalances(CONNECTION_ID, USER_ID);

        assertThat(balances).hasSize(2);
        assertThat(balances.getFirst().assetKey()).isEqualTo("BTC");
        assertThat(balances.getFirst().totalQuantity()).isEqualByComparingTo("0.5");
        assertThat(balances.getFirst().availableQuantity()).isEqualByComparingTo("0.4");
        assertThat(balances.getFirst().lockedQuantity()).isEqualByComparingTo("0.1");
        assertThat(balances.getFirst().totalQuantity())
                .isNotEqualByComparingTo(balances.getFirst().availableQuantity().add(balances.getFirst().lockedQuantity())
                        .add(new BigDecimal("0.02")));
        assertThat(balances.get(1).category()).hasToString("FIAT");
        assertThat(balances.get(1).totalQuantity()).isEqualByComparingTo("12000");
        assertThat(balances).allSatisfy(balance -> assertThat(balance.fetchedAt()).isEqualTo(NOW));
    }

    @Test
    void splitsFullTimestampWindowsAndReturnsEveryTradeInTheRequestedRange() {
        BitbankCredentialLoader credentialLoader = mock(BitbankCredentialLoader.class);
        BitbankRestClient restClient = mock(BitbankRestClient.class);
        BitbankCredentials credentials = credentials();
        when(credentialLoader.load(CONNECTION_ID, USER_ID)).thenReturn(credentials);
        when(restClient.fetchSpotPairs()).thenReturn(List.of(new BitbankDtos.SpotPair("btc_jpy", "btc", "jpy")));
        List<BitbankDtos.Trade> allTrades = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            allTrades.add(new BitbankDtos.Trade(
                    "trade-" + index,
                    "btc_jpy",
                    "buy",
                    null,
                    "limit",
                    BigDecimal.ONE,
                    new BigDecimal("100"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    1_000L + index));
        }
        when(restClient.fetchTrades(eq(credentials), anyLong(), anyLong())).thenAnswer(invocation -> {
            long since = invocation.getArgument(1);
            long end = invocation.getArgument(2);
            return allTrades.stream().filter(trade -> trade.executedAtMillis() >= since
                    && trade.executedAtMillis() <= end).toList();
        });
        when(restClient.fetchDeposits(any(BitbankCredentials.class), any(), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(restClient.fetchWithdrawals(any(BitbankCredentials.class), any(), anyLong(), anyLong()))
                .thenReturn(List.of());
        BitbankAdapter adapter = new BitbankAdapter(credentialLoader, restClient,
                new BitbankActivityNormalizer(), Clock.fixed(NOW, ZoneOffset.UTC));

        var activities = adapter.fetchActivities(CONNECTION_ID, USER_ID,
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_999));

        assertThat(activities).hasSize(1_000);
        assertThat(activities).extracting(activity -> activity.eventType())
                .containsOnly(NormalizedActivityType.BUY);
        verify(restClient, times(3)).fetchTrades(eq(credentials), anyLong(), anyLong());
        verify(restClient).fetchDeposits(credentials, null, 999L, 2_000L);
        verify(restClient).fetchDeposits(credentials, "jpy", 999L, 2_000L);
        verify(restClient).fetchWithdrawals(credentials, null, 999L, 2_000L);
        verify(restClient).fetchWithdrawals(credentials, "jpy", 999L, 2_000L);
    }

    private static BitbankCredentials credentials() {
        return new BitbankCredentials("fixture-key".getBytes(StandardCharsets.UTF_8),
                "fixture-secret".getBytes(StandardCharsets.UTF_8));
    }
}
