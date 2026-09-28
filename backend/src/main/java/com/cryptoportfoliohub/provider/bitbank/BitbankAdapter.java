package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedAssetCategory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.springframework.stereotype.Component;

@Component
public final class BitbankAdapter {

    private static final int MAX_HISTORY_REQUESTS_PER_ENDPOINT = 64;
    private static final String BITBANK_NETWORK = "BITBANK";

    private final BitbankCredentialLoader credentialLoader;
    private final BitbankRestClient restClient;
    private final BitbankActivityNormalizer activityNormalizer;
    private final Clock clock;

    public BitbankAdapter(
            BitbankCredentialLoader credentialLoader,
            BitbankRestClient restClient,
            BitbankActivityNormalizer activityNormalizer,
            Clock clock) {
        this.credentialLoader = credentialLoader;
        this.restClient = restClient;
        this.activityNormalizer = activityNormalizer;
        this.clock = clock;
    }

    public List<NormalizedAssetBalance> fetchBalances(UUID connectionId, UUID authenticatedUserId) {
        try (BitbankCredentials credentials = credentialLoader.load(connectionId, authenticatedUserId)) {
            List<NormalizedAssetBalance> balances = new ArrayList<>();
            Set<String> seenAssetKeys = new HashSet<>();
            Instant fetchedAt = clock.instant();
            for (BitbankDtos.Asset asset : restClient.fetchAssets(credentials)) {
                String assetKey = assetKey(asset.asset());
                if (!seenAssetKeys.add(assetKey)) {
                    throw invalidResponse();
                }
                BigDecimal total = quantity(asset.onhandAmount());
                BigDecimal available = quantity(asset.freeAmount());
                BigDecimal locked = quantity(asset.lockedAmount());
                quantity(asset.withdrawingAmount()); // This is a subset of locked/onhand and is not added again.
                if (asset.amountPrecision() != null && asset.amountPrecision() < 0) {
                    throw invalidResponse();
                }
                balances.add(new NormalizedAssetBalance(
                        assetKey,
                        assetKey,
                        null,
                        "JPY".equals(assetKey) ? NormalizedAssetCategory.FIAT : NormalizedAssetCategory.CRYPTO,
                        BITBANK_NETWORK,
                        asset.asset().toLowerCase(Locale.ROOT),
                        total,
                        available,
                        locked,
                        fetchedAt));
            }
            return List.copyOf(balances);
        }
    }

    public List<com.cryptoportfoliohub.provider.NormalizedActivity> fetchActivities(
            UUID connectionId,
            UUID authenticatedUserId,
            Instant fromInclusive,
            Instant toInclusive) {
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            throw new IllegalArgumentException("A valid activity window is required.");
        }
        long fromMillis = fromInclusive.toEpochMilli();
        long toMillis = toInclusive.toEpochMilli();
        try (BitbankCredentials credentials = credentialLoader.load(connectionId, authenticatedUserId)) {
            List<BitbankDtos.SpotPair> pairs = restClient.fetchSpotPairs();
            List<BitbankDtos.Trade> trades = completeHistoryWindow(
                    window -> restClient.fetchTrades(credentials,
                            queryStart(window.startMillis()), queryEnd(window.endMillis())),
                    BitbankDtos.Trade::executedAtMillis,
                    fromMillis,
                    toMillis,
                    1_000);
            List<BitbankDtos.Deposit> deposits = new ArrayList<>();
            deposits.addAll(completeHistoryWindow(
                    window -> restClient.fetchDeposits(credentials, null,
                            queryStart(window.startMillis()), queryEnd(window.endMillis())),
                    BitbankDtos.Deposit::foundAtMillis,
                    fromMillis,
                    toMillis,
                    100));
            deposits.addAll(completeHistoryWindow(
                    window -> restClient.fetchDeposits(credentials, "jpy",
                            queryStart(window.startMillis()), queryEnd(window.endMillis())),
                    BitbankDtos.Deposit::foundAtMillis,
                    fromMillis,
                    toMillis,
                    100));
            List<BitbankDtos.Withdrawal> withdrawals = new ArrayList<>();
            withdrawals.addAll(completeHistoryWindow(
                    window -> restClient.fetchWithdrawals(credentials, null,
                            queryStart(window.startMillis()), queryEnd(window.endMillis())),
                    BitbankDtos.Withdrawal::requestedAtMillis,
                    fromMillis,
                    toMillis,
                    100));
            withdrawals.addAll(completeHistoryWindow(
                    window -> restClient.fetchWithdrawals(credentials, "jpy",
                            queryStart(window.startMillis()), queryEnd(window.endMillis())),
                    BitbankDtos.Withdrawal::requestedAtMillis,
                    fromMillis,
                    toMillis,
                    100));
            return activityNormalizer.normalize(trades, deposits, withdrawals, pairs,
                    fromInclusive, toInclusive);
        }
    }

    private static <T> List<T> completeHistoryWindow(
            Function<Window, List<T>> fetch,
            ToLongFunction<T> eventTime,
            long startMillis,
            long endMillis,
            int providerLimit) {
        return completeHistoryWindow(fetch, eventTime, new Window(startMillis, endMillis),
                providerLimit, new RequestBudget());
    }

    private static <T> List<T> completeHistoryWindow(
            Function<Window, List<T>> fetch,
            ToLongFunction<T> eventTime,
            Window window,
            int providerLimit,
            RequestBudget budget) {
        if (++budget.requestCount > MAX_HISTORY_REQUESTS_PER_ENDPOINT) {
            throw new ProviderException(ProviderErrorCategory.UNAVAILABLE);
        }
        List<T> rows = fetch.apply(window);
        if (rows.size() < providerLimit) {
            return rows.stream()
                    .filter(row -> window.contains(eventTime.applyAsLong(row)))
                    .toList();
        }
        if (window.startMillis() >= window.endMillis()) {
            // A provider cap at a single millisecond cannot be continued without a documented event-ID cursor.
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        long midpoint = window.startMillis() + ((window.endMillis() - window.startMillis()) / 2);
        Window left = new Window(window.startMillis(), midpoint);
        Window right = new Window(midpoint + 1, window.endMillis());
        List<T> result = new ArrayList<>();
        result.addAll(completeHistoryWindow(fetch, eventTime, left, providerLimit, budget));
        result.addAll(completeHistoryWindow(fetch, eventTime, right, providerLimit, budget));
        return List.copyOf(result);
    }

    private static long queryStart(long windowStart) {
        return windowStart == 0 ? 0 : windowStart - 1;
    }

    private static long queryEnd(long windowEnd) {
        return windowEnd == Long.MAX_VALUE ? windowEnd : windowEnd + 1;
    }

    private static String assetKey(String asset) {
        if (asset == null || asset.isBlank()) {
            throw invalidResponse();
        }
        String key = asset.trim().toUpperCase(Locale.ROOT);
        if (key.length() > 32) {
            throw invalidResponse();
        }
        return key;
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw invalidResponse();
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 18 || normalized.precision() > 38
                || normalized.precision() - normalized.scale() > 20) {
            throw invalidResponse();
        }
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }

    private record Window(long startMillis, long endMillis) {
        private boolean contains(long eventTimeMillis) {
            return eventTimeMillis >= startMillis && eventTimeMillis <= endMillis;
        }
    }

    private static final class RequestBudget {
        private int requestCount;
    }
}
