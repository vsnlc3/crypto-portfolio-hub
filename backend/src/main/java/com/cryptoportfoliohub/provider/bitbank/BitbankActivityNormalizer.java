package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class BitbankActivityNormalizer {

    private static final String NETWORK = "BITBANK";

    public List<NormalizedActivity> normalize(
            List<BitbankDtos.Trade> trades,
            List<BitbankDtos.Deposit> deposits,
            List<BitbankDtos.Withdrawal> withdrawals,
            List<BitbankDtos.SpotPair> spotPairs,
            Instant fromInclusive,
            Instant toInclusive) {
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            throw new IllegalArgumentException("A valid activity window is required.");
        }
        Map<String, PairAssets> pairAssets = pairAssets(spotPairs);
        Map<String, NormalizedActivity> activities = new HashMap<>();
        for (BitbankDtos.Trade trade : trades) {
            addIfInWindow(activities, normalizeTrade(trade, pairAssets), fromInclusive, toInclusive);
        }
        for (BitbankDtos.Deposit deposit : deposits) {
            addIfInWindow(activities, normalizeDeposit(deposit), fromInclusive, toInclusive);
        }
        for (BitbankDtos.Withdrawal withdrawal : withdrawals) {
            addIfInWindow(activities, normalizeWithdrawal(withdrawal), fromInclusive, toInclusive);
        }
        return activities.values().stream()
                .sorted(java.util.Comparator.comparing(NormalizedActivity::occurredAt)
                        .thenComparing(NormalizedActivity::dedupKey))
                .toList();
    }

    private static NormalizedActivity normalizeTrade(
            BitbankDtos.Trade trade, Map<String, PairAssets> pairAssets) {
        if (trade.tradeId() == null || trade.tradeId().isBlank()
                || trade.pair() == null || trade.pair().isBlank()
                || trade.side() == null || trade.type() == null) {
            throw invalidResponse();
        }
        Instant occurredAt = epochMillis(trade.executedAtMillis());
        String originalType = "SPOT_TRADE:" + trade.type();
        if (trade.positionSide() != null && !trade.positionSide().isBlank()) {
            return header(trade.tradeId(), "trade:" + trade.tradeId(), NormalizedActivityType.OTHER,
                    originalType + ":POSITION_SIDE:" + trade.positionSide(), null, occurredAt, List.of());
        }

        PairAssets pair = pairAssets.get(trade.pair());
        if (pair == null) {
            throw invalidResponse();
        }
        BigDecimal amount = positive(trade.amount());
        BigDecimal price = positive(trade.price());
        BigDecimal quoteAmount = fitDb(amount.multiply(price));
        BigDecimal baseFee = nonNegative(trade.feeAmountBase());
        BigDecimal quoteFee = nonNegative(trade.feeAmountQuote());
        nonNegative(trade.feeOccurredAmountQuote()); // Kept distinct in the wire DTO; spot fees use fee_amount_quote once.

        List<NormalizedActivityLeg> legs = new ArrayList<>();
        NormalizedActivityType eventType;
        if ("buy".equalsIgnoreCase(trade.side())) {
            eventType = NormalizedActivityType.BUY;
            addLeg(legs, NormalizedDirection.OUT, pair.quote(), quoteAmount);
            addLeg(legs, NormalizedDirection.IN, pair.base(), amount);
        } else if ("sell".equalsIgnoreCase(trade.side())) {
            eventType = NormalizedActivityType.SELL;
            addLeg(legs, NormalizedDirection.OUT, pair.base(), amount);
            addLeg(legs, NormalizedDirection.IN, pair.quote(), quoteAmount);
        } else {
            return header(trade.tradeId(), "trade:" + trade.tradeId(), NormalizedActivityType.OTHER,
                    originalType + ":SIDE:" + trade.side(), null, occurredAt, List.of());
        }
        addFee(legs, pair.base(), baseFee);
        // The official spot response defines fee_occurred_amount_quote as the same value; never double-count it.
        addFee(legs, pair.quote(), quoteFee);
        return header(trade.tradeId(), "trade:" + trade.tradeId(), eventType,
                originalType, null, occurredAt, legs);
    }

    private static NormalizedActivity normalizeDeposit(BitbankDtos.Deposit deposit) {
        if (deposit.uuid() == null || deposit.uuid().isBlank()) {
            throw invalidResponse();
        }
        Instant occurredAt = epochMillis(deposit.foundAtMillis());
        List<NormalizedActivityLeg> legs = new ArrayList<>();
        addTransferLegIfKnown(legs, NormalizedDirection.IN, deposit.asset(), deposit.amount());
        String originalType = isBlank(deposit.category()) ? "DEPOSIT" : "DEPOSIT:" + deposit.category();
        return header(deposit.uuid(), "deposit:" + deposit.uuid(), NormalizedActivityType.DEPOSIT,
                originalType, nullable(deposit.status()), occurredAt, legs);
    }

    private static NormalizedActivity normalizeWithdrawal(BitbankDtos.Withdrawal withdrawal) {
        if (withdrawal.uuid() == null || withdrawal.uuid().isBlank()) {
            throw invalidResponse();
        }
        nonNegative(withdrawal.fee());
        Instant occurredAt = epochMillis(withdrawal.requestedAtMillis());
        List<NormalizedActivityLeg> legs = new ArrayList<>();
        addTransferLegIfKnown(legs, NormalizedDirection.OUT, withdrawal.asset(), withdrawal.amount());
        // The REST response includes a fee amount but does not document its currency. Do not invent a FEE leg.
        return header(withdrawal.uuid(), "withdrawal:" + withdrawal.uuid(), NormalizedActivityType.WITHDRAW,
                "WITHDRAWAL", nullable(withdrawal.status()), occurredAt, legs);
    }

    private static void addTransferLegIfKnown(
            List<NormalizedActivityLeg> legs, NormalizedDirection direction, String asset, BigDecimal amount) {
        if (isBlank(asset) || amount == null) {
            return;
        }
        addLeg(legs, direction, assetKey(asset), positive(amount));
    }

    private static Map<String, PairAssets> pairAssets(List<BitbankDtos.SpotPair> spotPairs) {
        Map<String, PairAssets> pairs = new HashMap<>();
        for (BitbankDtos.SpotPair pair : spotPairs) {
            if (pair == null || isBlank(pair.name()) || isBlank(pair.baseAsset()) || isBlank(pair.quoteAsset())
                    || pairs.putIfAbsent(pair.name(),
                            new PairAssets(assetKey(pair.baseAsset()), assetKey(pair.quoteAsset()))) != null) {
                throw invalidResponse();
            }
        }
        return Map.copyOf(pairs);
    }

    private static void addFee(List<NormalizedActivityLeg> legs, String asset, BigDecimal amount) {
        if (amount.signum() > 0) {
            addLeg(legs, NormalizedDirection.FEE, asset, amount);
        }
    }

    private static void addLeg(
            List<NormalizedActivityLeg> legs, NormalizedDirection direction, String asset, BigDecimal amount) {
        String key = assetKey(asset);
        if (key.length() > 8) {
            throw invalidResponse();
        }
        BigDecimal value = fitDb(positive(amount));
        legs.add(new NormalizedActivityLeg(
                legs.size(), direction, key, key, value, value, key));
    }

    private static NormalizedActivity header(
            String eventId,
            String dedupKey,
            NormalizedActivityType eventType,
            String originalEventType,
            String status,
            Instant occurredAt,
            List<NormalizedActivityLeg> legs) {
        return new NormalizedActivity(eventId, dedupKey, eventType,
                originalEventType, status, occurredAt, legs);
    }

    private static void addIfInWindow(
            Map<String, NormalizedActivity> activities,
            NormalizedActivity activity,
            Instant fromInclusive,
            Instant toInclusive) {
        if (activity.occurredAt().isBefore(fromInclusive) || activity.occurredAt().isAfter(toInclusive)) {
            return;
        }
        if (activities.putIfAbsent(activity.dedupKey(), activity) != null) {
            throw invalidResponse();
        }
    }

    private static String assetKey(String asset) {
        if (isBlank(asset)) {
            throw invalidResponse();
        }
        String key = asset.trim().toUpperCase(Locale.ROOT);
        if (key.length() > 32) {
            throw invalidResponse();
        }
        return key;
    }

    private static BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw invalidResponse();
        }
        return fitDb(amount);
    }

    private static BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw invalidResponse();
        }
        return fitDb(amount);
    }

    private static BigDecimal fitDb(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > 18 || normalized.precision() > 38
                || normalized.precision() - normalized.scale() > 20) {
            throw invalidResponse();
        }
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static Instant epochMillis(long millis) {
        if (millis < 0) {
            throw invalidResponse();
        }
        try {
            return Instant.ofEpochMilli(millis);
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private static String nullable(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }

    private record PairAssets(String base, String quote) {
    }
}
