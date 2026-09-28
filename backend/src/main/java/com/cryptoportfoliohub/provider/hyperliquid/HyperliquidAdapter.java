package com.cryptoportfoliohub.provider.hyperliquid;

import com.cryptoportfoliohub.domain.money.PositionSide;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.provider.ActivityPage;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedAssetCategory;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillDetail;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillDirection;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillSide;
import com.cryptoportfoliohub.provider.NormalizedPerpetualPosition;
import com.cryptoportfoliohub.provider.NormalizedProviderAccountState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Read-only normalizer for Hyperliquid Info API data. */
@Component
public class HyperliquidAdapter {

    private static final int FILL_LIMIT = 2_000;
    private static final int FUNDING_LIMIT = 500;
    private static final int MAX_HISTORY_REQUESTS = 256;
    private final HyperliquidInfoClient client;
    private final Clock clock;

    public HyperliquidAdapter(HyperliquidInfoClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public HyperliquidCurrentState fetchCurrentState(String accountAddress) {
        String address = validAddress(accountAddress);
        Instant fetchedAt = clock.instant();
        HyperliquidAccountModes.Mapping mode = readAccountMode(address);
        SpotMetadata spotMetadata = SpotMetadata.parse(query("spotMeta"));
        JsonNode spotState = query("spotClearinghouseState", Map.of("user", address));
        List<NormalizedAssetBalance> balances = normalizeSpotBalances(spotMetadata, spotState, fetchedAt);

        List<NormalizedProviderAccountState> accountStates = new ArrayList<>();
        accountStates.add(new NormalizedProviderAccountState(
                "ACCOUNT", mode.mode().name(), mode.providerValue(), null,
                null, null, null, null, null, fetchedAt));
        List<NormalizedPerpetualPosition> positions = new ArrayList<>();
        for (String dex : fetchPerpDexNames()) {
            JsonNode metaAndContexts = query("metaAndAssetCtxs", dexRequest(dex));
            PerpMarketMetadata metadata = PerpMarketMetadata.parse(metaAndContexts, spotMetadata, dex);
            JsonNode state = query("clearinghouseState", userDexRequest(address, dex));
            AccountAndPositions normalized = normalizePerpetualState(metadata, state, mode, fetchedAt);
            accountStates.add(normalized.accountState());
            positions.addAll(normalized.positions());
        }
        return new HyperliquidCurrentState(
                mode.mode(), mode.providerValue(), isKnown(mode.mode()),
                balances, accountStates, positions);
    }

    public ActivityPage fetchActivities(String accountAddress, Instant fromInclusive, Instant toInclusive) {
        String address = validAddress(accountAddress);
        if (fromInclusive == null || toInclusive == null || fromInclusive.isAfter(toInclusive)
                || fromInclusive.isBefore(Instant.EPOCH) || toInclusive.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Activity range must be a valid past time range.");
        }
        long start = fromInclusive.toEpochMilli();
        long end = toInclusive.toEpochMilli();
        RequestBudget budget = new RequestBudget();
        List<JsonNode> fills = collectTimeRange(
                address, "userFillsByTime", start, end, FILL_LIMIT, budget);
        List<JsonNode> fundings = collectTimeRange(
                address, "userFunding", start, end, FUNDING_LIMIT, budget);
        SpotMetadata spotMetadata = SpotMetadata.parse(query("spotMeta"));
        Map<String, PerpMarketMetadata> markets = readPerpMarketMetadata(spotMetadata);
        List<NormalizedActivity> normalized = new ArrayList<>();
        Set<String> dedupKeys = new HashSet<>();
        for (JsonNode fill : fills) {
            NormalizedActivity activity = normalizeFill(fill, spotMetadata, markets);
            addUnique(normalized, dedupKeys, activity);
        }
        for (JsonNode funding : fundings) {
            NormalizedActivity activity = normalizeFunding(funding, spotMetadata);
            if (activity != null) {
                addUnique(normalized, dedupKeys, activity);
            }
        }
        normalized.sort((left, right) -> {
            int time = left.occurredAt().compareTo(right.occurredAt());
            return time != 0 ? time : left.dedupKey().compareTo(right.dedupKey());
        });
        // Hyperliquid exposes only the latest 10,000 fills. Mark a saturated result as potentially incomplete.
        return new ActivityPage(normalized, null, fills.size() >= 10_000);
    }

    private HyperliquidAccountModes.Mapping readAccountMode(String address) {
        try {
            JsonNode response = query("userAbstraction", Map.of("user", address));
            return HyperliquidAccountModes.map(response.isTextual() ? response.asText() : null);
        } catch (ProviderException exception) {
            return HyperliquidAccountModes.map(null);
        }
    }

    private List<String> fetchPerpDexNames() {
        JsonNode response = query("perpDexs");
        if (!response.isArray()) {
            throw invalidResponse();
        }
        List<String> names = new ArrayList<>();
        names.add(""); // The first perp DEX has an empty API name.
        Set<String> seen = new HashSet<>(Set.of(""));
        for (JsonNode dex : response) {
            if (dex == null || dex.isNull()) {
                continue;
            }
            String name = requiredText(dex.path("name"));
            if (!seen.add(name)) {
                throw invalidResponse();
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    private Map<String, PerpMarketMetadata> readPerpMarketMetadata(SpotMetadata spotMetadata) {
        Map<String, PerpMarketMetadata> markets = new HashMap<>();
        for (String dex : fetchPerpDexNames()) {
            PerpMarketMetadata metadata = PerpMarketMetadata.parse(
                    query("metaAndAssetCtxs", dexRequest(dex)), spotMetadata, dex);
            for (String coin : metadata.marketNames()) {
                if (markets.putIfAbsent(coin, metadata) != null) {
                    throw invalidResponse();
                }
            }
        }
        return Map.copyOf(markets);
    }

    private AccountAndPositions normalizePerpetualState(
            PerpMarketMetadata metadata,
            JsonNode state,
            HyperliquidAccountModes.Mapping mode,
            Instant fetchedAt) {
        String accountScope = metadata.dex().isBlank()
                ? "PERP_DEX:DEFAULT" : "PERP_DEX:" + metadata.dex();
        boolean standard = mode.mode() == HyperliquidAccountMode.STANDARD;
        BigDecimal accountEquity = standard ? optionalDecimal(state.path("marginSummary").path("accountValue")) : null;
        NormalizedProviderAccountState accountState = new NormalizedProviderAccountState(
                accountScope,
                mode.mode().name(),
                mode.providerValue(),
                metadata.collateralCurrency(),
                null,
                null,
                accountEquity,
                null,
                accountEquity == null ? null : standard,
                fetchedAt);

        JsonNode rows = state.path("assetPositions");
        if (!rows.isArray()) {
            throw invalidResponse();
        }
        List<NormalizedPerpetualPosition> positions = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode position = row.path("position");
            String coin = requiredText(position.path("coin"));
            BigDecimal signedQuantity = requiredDecimal(position.path("szi"));
            if (signedQuantity.signum() == 0) {
                continue;
            }
            String instrument = normalizedInstrument(metadata.dex(), coin);
            BigDecimal quantity = signedQuantity.abs();
            BigDecimal markPrice = metadata.markPrices().get(coin);
            String priceCurrency = priceCurrency(metadata.dex(), coin);
            String collateralCurrency = metadata.collateralCurrency();
            BigDecimal leverage = optionalDecimal(position.path("leverage").path("value"));
            BigDecimal unrealizedPnl = optionalDecimal(position.path("unrealizedPnl"));
            positions.add(new NormalizedPerpetualPosition(
                    metadata.dex().isBlank() ? "DEFAULT:" + coin : coin,
                    instrument,
                    signedQuantity.signum() > 0 ? PositionSide.LONG : PositionSide.SHORT,
                    quantity,
                    optionalDecimal(position.path("entryPx")),
                    markPrice,
                    optionalDecimal(position.path("liquidationPx")),
                    priceCurrency,
                    leverage,
                    optionalDecimal(position.path("marginUsed")),
                    collateralCurrency,
                    unrealizedPnl,
                    collateralCurrency,
                    fetchedAt));
        }
        return new AccountAndPositions(accountState, List.copyOf(positions));
    }

    private NormalizedActivity normalizeFill(
            JsonNode fill,
            SpotMetadata spotMetadata,
            Map<String, PerpMarketMetadata> markets) {
        String coin = requiredText(fill.path("coin"));
        long time = requiredLong(fill.path("time"));
        String tid = requiredText(fill.path("tid"));
        String hash = requiredText(fill.path("hash"));
        String providerEventId = hash + ":" + tid;
        String dedupKey = "fill:" + time + ":" + coin + ":" + tid;
        Instant occurredAt = Instant.ofEpochMilli(time);
        BigDecimal size = positiveDecimal(fill.path("sz"));
        BigDecimal price = positiveDecimal(fill.path("px"));
        String side = requiredText(fill.path("side"));

        SpotMarket market = spotMetadata.findMarket(coin);
        if (market != null) {
            if (!"B".equals(side) && !"A".equals(side)) {
                throw invalidResponse();
            }
            List<NormalizedActivityLeg> legs = new ArrayList<>();
            BigDecimal quoteQuantity = normalizeDecimal(size.multiply(price));
            if ("B".equals(side)) {
                legs.add(leg(0, NormalizedDirection.IN, market.base(), size));
                legs.add(leg(1, NormalizedDirection.OUT, market.quote(), quoteQuantity));
            } else {
                legs.add(leg(0, NormalizedDirection.OUT, market.base(), size));
                legs.add(leg(1, NormalizedDirection.IN, market.quote(), quoteQuantity));
            }
            appendFeeLeg(legs, 2, fill, spotMetadata);
            return new NormalizedActivity(providerEventId, dedupKey,
                    "B".equals(side) ? NormalizedActivityType.BUY : NormalizedActivityType.SELL,
                    "spotFill", "COMPLETED", occurredAt, legs);
        }

        if (!"B".equals(side) && !"A".equals(side)) {
            throw invalidResponse();
        }
        PerpMarketMetadata marketMetadata = markets.get(coin);
        if (marketMetadata == null) {
            throw invalidResponse();
        }
        String dex = marketMetadata.dex();
        String currency = priceCurrency(dex, coin);
        String pnlCurrency = marketMetadata.collateralCurrency();
        NormalizedPerpetualFillDetail detail = new NormalizedPerpetualFillDetail(
                normalizedInstrument(dex, coin),
                "B".equals(side) ? NormalizedPerpetualFillSide.BUY : NormalizedPerpetualFillSide.SELL,
                fillDirection(optionalText(fill.path("dir"))),
                optionalText(fill.path("dir")),
                size,
                price,
                currency,
                optionalDecimal(fill.path("startPosition")),
                optionalDecimal(fill.path("closedPnl")),
                pnlCurrency);
        List<NormalizedActivityLeg> legs = new ArrayList<>();
        appendFeeLeg(legs, 0, fill, spotMetadata);
        return new NormalizedActivity(providerEventId, dedupKey, NormalizedActivityType.PERP,
                "perpFill", "COMPLETED", occurredAt, legs, detail);
    }

    private NormalizedActivity normalizeFunding(JsonNode funding, SpotMetadata spotMetadata) {
        JsonNode delta = funding.path("delta");
        String type = requiredText(delta.path("type"));
        if (!"funding".equals(type)) {
            throw invalidResponse();
        }
        String coin = requiredText(delta.path("coin"));
        long time = requiredLong(funding.path("time"));
        String hash = requiredText(funding.path("hash"));
        BigDecimal signedAmount = requiredDecimal(delta.path("usdc"));
        String dedupKey = "funding:" + time + ":" + hash + ":" + coin;
        if (signedAmount.signum() == 0) {
            return new NormalizedActivity(hash, dedupKey, NormalizedActivityType.FUNDING,
                    "userFunding", "COMPLETED", Instant.ofEpochMilli(time), List.of());
        }
        Token usdc = spotMetadata.findUniqueToken("USDC");
        List<NormalizedActivityLeg> legs = List.of(leg(0,
                signedAmount.signum() > 0 ? NormalizedDirection.IN : NormalizedDirection.OUT,
                usdc, signedAmount.abs()));
        return new NormalizedActivity(hash, dedupKey, NormalizedActivityType.FUNDING,
                "userFunding", "COMPLETED", Instant.ofEpochMilli(time), legs);
    }

    private List<JsonNode> collectTimeRange(
            String address, String type, long start, long end, int limit, RequestBudget budget) {
        if (++budget.count > MAX_HISTORY_REQUESTS) {
            throw new ProviderException(ProviderErrorCategory.UNAVAILABLE);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("user", address);
        request.put("startTime", start);
        request.put("endTime", end);
        if ("userFillsByTime".equals(type)) {
            request.put("aggregateByTime", false);
        }
        JsonNode response = client.query(request);
        if (!response.isArray()) {
            throw invalidResponse();
        }
        if (response.size() < limit) {
            List<JsonNode> inRange = new ArrayList<>();
            for (JsonNode row : response) {
                long rowTime = requiredLong(row.path("time"));
                if (rowTime >= start && rowTime <= end) {
                    inRange.add(row);
                }
            }
            return inRange;
        }
        if (start == end) {
            // The API provides no event-id cursor for a same-millisecond overflow.
            throw new ProviderException(ProviderErrorCategory.UNAVAILABLE);
        }
        long midpoint = start + (end - start) / 2;
        List<JsonNode> result = new ArrayList<>();
        result.addAll(collectTimeRange(address, type, start, midpoint, limit, budget));
        result.addAll(collectTimeRange(address, type, midpoint + 1, end, limit, budget));
        return List.copyOf(result);
    }

    private JsonNode query(String type) {
        return query(type, Map.of());
    }

    private JsonNode query(String type, Map<String, Object> parameters) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.putAll(parameters);
        return client.query(request);
    }

    private static List<NormalizedAssetBalance> normalizeSpotBalances(
            SpotMetadata metadata, JsonNode state, Instant fetchedAt) {
        JsonNode rows = state.path("balances");
        if (!rows.isArray()) {
            throw invalidResponse();
        }
        List<NormalizedAssetBalance> balances = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            int tokenIndex = requiredInt(row.path("token"));
            Token token = metadata.tokenByIndex().get(tokenIndex);
            if (token == null) {
                throw invalidResponse();
            }
            String providerCoin = requiredText(row.path("coin"));
            if (!providerCoin.equals(token.symbol())) {
                throw invalidResponse();
            }
            BigDecimal total = nonNegativeDecimal(row.path("total"));
            BigDecimal hold = nonNegativeDecimal(row.path("hold"));
            if (hold.compareTo(total) > 0) {
                throw invalidResponse();
            }
            if (!seen.add(token.assetKey())) {
                throw invalidResponse();
            }
            balances.add(new NormalizedAssetBalance(
                    token.assetKey(), token.symbol(), token.fullName(),
                    NormalizedAssetCategory.CRYPTO, "HYPERLIQUID", token.tokenId(),
                    total, normalizeDecimal(total.subtract(hold)), hold, fetchedAt));
        }
        return List.copyOf(balances);
    }

    private static void appendFeeLeg(
            List<NormalizedActivityLeg> legs, int legIndex, JsonNode fill, SpotMetadata metadata) {
        BigDecimal fee = requiredDecimal(fill.path("fee"));
        if (fee.signum() == 0) {
            return;
        }
        String feeSymbol = requiredText(fill.path("feeToken")).trim();
        Token token = metadata.findUniqueToken(feeSymbol);
        NormalizedDirection direction = fee.signum() > 0 ? NormalizedDirection.FEE : NormalizedDirection.IN;
        legs.add(leg(legIndex, direction, token, normalizeDecimal(fee.abs())));
    }

    private static NormalizedActivityLeg leg(
            int index, NormalizedDirection direction, Token token, BigDecimal quantity) {
        return new NormalizedActivityLeg(index, direction, token.assetKey(), token.symbol(), quantity,
                quantity, token.symbol());
    }

    private static String priceCurrency(String dex, String coin) {
        if (!dex.isBlank()) {
            return null; // HIP-3 market oracle denomination is market-specific and not present in Info metadata.
        }
        return switch (coin.toUpperCase(Locale.ROOT)) {
            case "HYPE", "PURR", "HYPE-USD", "PURR-USD" -> "USDC";
            default -> "USDT";
        };
    }

    private static String normalizedInstrument(String dex, String coin) {
        return dex.isBlank() ? coin : dex + ":" + coin.substring(coin.indexOf(':') + 1);
    }

    private static NormalizedPerpetualFillDirection fillDirection(String direction) {
        if (direction == null) {
            return NormalizedPerpetualFillDirection.UNKNOWN;
        }
        return switch (direction) {
            case "Open Long" -> NormalizedPerpetualFillDirection.OPEN_LONG;
            case "Close Long" -> NormalizedPerpetualFillDirection.CLOSE_LONG;
            case "Open Short" -> NormalizedPerpetualFillDirection.OPEN_SHORT;
            case "Close Short" -> NormalizedPerpetualFillDirection.CLOSE_SHORT;
            default -> NormalizedPerpetualFillDirection.UNKNOWN;
        };
    }

    private static boolean isKnown(HyperliquidAccountMode mode) {
        return mode == HyperliquidAccountMode.STANDARD
                || mode == HyperliquidAccountMode.UNIFIED_ACCOUNT
                || mode == HyperliquidAccountMode.PORTFOLIO_MARGIN;
    }

    private static Map<String, Object> dexRequest(String dex) {
        return dex.isBlank() ? Map.of() : Map.of("dex", dex);
    }

    private static Map<String, Object> userDexRequest(String address, String dex) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("user", address);
        if (!dex.isBlank()) {
            request.put("dex", dex);
        }
        return request;
    }

    private static String validAddress(String value) {
        if (value == null || !value.trim().matches("(?i)^0x[0-9a-f]{40}$")) {
            throw new IllegalArgumentException("A Hyperliquid account address must be a 20-byte hex address.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void addUnique(List<NormalizedActivity> activities, Set<String> keys, NormalizedActivity value) {
        if (!keys.add(value.dedupKey())) {
            throw invalidResponse();
        }
        activities.add(value);
    }

    private static BigDecimal requiredDecimal(JsonNode node) {
        BigDecimal value = optionalDecimal(node);
        if (value == null) {
            throw invalidResponse();
        }
        return value;
    }

    private static BigDecimal positiveDecimal(JsonNode node) {
        BigDecimal value = requiredDecimal(node);
        if (value.signum() <= 0) {
            throw invalidResponse();
        }
        return value;
    }

    private static BigDecimal nonNegativeDecimal(JsonNode node) {
        BigDecimal value = requiredDecimal(node);
        if (value.signum() < 0) {
            throw invalidResponse();
        }
        return value;
    }

    private static BigDecimal optionalDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(node.asText());
            return normalizeDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static BigDecimal normalizeDecimal(BigDecimal value) {
        if (value.precision() > 38 || value.scale() > 18 || value.precision() - value.scale() > 20) {
            throw invalidResponse();
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static String requiredText(JsonNode node) {
        String value = optionalText(node);
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private static String optionalText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static long requiredLong(JsonNode node) {
        try {
            long value = Long.parseLong(node.asText());
            if (value < 0) {
                throw invalidResponse();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static int requiredInt(JsonNode node) {
        try {
            return Integer.parseInt(node.asText());
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }

    private record AccountAndPositions(
            NormalizedProviderAccountState accountState,
            List<NormalizedPerpetualPosition> positions) {
    }

    private static final class RequestBudget {
        private int count;
    }

    private record Token(int index, String tokenId, String symbol, String fullName) {
        private String assetKey() {
            return "HYPERLIQUID:SPOT:" + tokenId.toLowerCase(Locale.ROOT);
        }
    }

    private record SpotMarket(int index, Token base, Token quote) {
    }

    private record SpotMetadata(
            Map<Integer, Token> tokenByIndex,
            Map<String, SpotMarket> marketByIdentifier) {
        private static SpotMetadata parse(JsonNode response) {
            JsonNode tokenRows = response.path("tokens");
            JsonNode marketRows = response.path("universe");
            if (!tokenRows.isArray() || !marketRows.isArray()) {
                throw invalidResponse();
            }
            Map<Integer, Token> tokens = new HashMap<>();
            for (JsonNode row : tokenRows) {
                int index = requiredInt(row.path("index"));
                String tokenId = requiredText(row.path("tokenId"));
                String symbol = requiredText(row.path("name"));
                String fullName = optionalText(row.path("fullName"));
                Token token = new Token(index, tokenId, symbol, fullName == null ? symbol : fullName);
                if (index < 0 || !tokenId.matches("(?i)^0x[0-9a-f]{32}$")
                        || tokens.putIfAbsent(index, token) != null) {
                    throw invalidResponse();
                }
            }
            Map<String, SpotMarket> markets = new HashMap<>();
            for (JsonNode row : marketRows) {
                int index = requiredInt(row.path("index"));
                String name = requiredText(row.path("name"));
                JsonNode pairTokens = row.path("tokens");
                if (!pairTokens.isArray() || pairTokens.size() != 2 || index < 0) {
                    throw invalidResponse();
                }
                Token base = tokens.get(requiredInt(pairTokens.get(0)));
                Token quote = tokens.get(requiredInt(pairTokens.get(1)));
                if (base == null || quote == null) {
                    throw invalidResponse();
                }
                SpotMarket market = new SpotMarket(index, base, quote);
                if (markets.putIfAbsent(name, market) != null
                        || markets.putIfAbsent("@" + index, market) != null) {
                    throw invalidResponse();
                }
            }
            return new SpotMetadata(Map.copyOf(tokens), Map.copyOf(markets));
        }

        private SpotMarket findMarket(String identifier) {
            return marketByIdentifier.get(identifier);
        }

        private Token findUniqueToken(String symbol) {
            List<Token> found = tokenByIndex.values().stream()
                    .filter(token -> token.symbol().equalsIgnoreCase(symbol))
                    .toList();
            if (found.size() != 1) {
                throw invalidResponse();
            }
            return found.getFirst();
        }
    }

    private record PerpMarketMetadata(
            String dex,
            String collateralCurrency,
            Map<String, BigDecimal> markPrices,
            Set<String> marketNames) {
        private static PerpMarketMetadata parse(JsonNode response, SpotMetadata spotMetadata, String dex) {
            if (!response.isArray() || response.size() != 2) {
                throw invalidResponse();
            }
            JsonNode meta = response.get(0);
            JsonNode universe = meta.path("universe");
            JsonNode contexts = response.get(1);
            if (!universe.isArray() || !contexts.isArray() || universe.size() != contexts.size()) {
                throw invalidResponse();
            }
            int collateralToken = requiredInt(meta.path("collateralToken"));
            Token collateral = spotMetadata.tokenByIndex().get(collateralToken);
            Map<String, BigDecimal> marks = new HashMap<>();
            Set<String> marketNames = new HashSet<>();
            for (int index = 0; index < universe.size(); index++) {
                String coin = requiredText(universe.get(index).path("name"));
                if (!marketNames.add(coin)) {
                    throw invalidResponse();
                }
                JsonNode markNode = contexts.get(index).path("markPx");
                BigDecimal mark = optionalDecimal(markNode);
                if (mark != null && mark.signum() <= 0) {
                    throw invalidResponse();
                }
                if (mark != null) {
                    marks.put(coin, mark);
                }
            }
            return new PerpMarketMetadata(dex, collateral == null ? null : collateral.symbol(),
                    Map.copyOf(marks), Set.copyOf(marketNames));
        }
    }
}
