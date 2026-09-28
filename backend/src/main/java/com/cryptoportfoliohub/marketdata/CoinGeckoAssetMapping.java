package com.cryptoportfoliohub.marketdata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CoinGeckoAssetMapping {

    private static final Map<String, String> COIN_IDS = Map.of(
            "BTC", "bitcoin",
            "ETH", "ethereum",
            "SOL", "solana",
            "XRP", "ripple",
            "HYPE", "hyperliquid",
            "USDC", "usd-coin",
            "USDT", "tether");

    private CoinGeckoAssetMapping() {
    }

    public static Optional<String> coinIdFor(String canonicalAssetKey) {
        return Optional.ofNullable(COIN_IDS.get(canonicalAssetKey));
    }

    public static List<String> allCoinIds() {
        return List.of("bitcoin", "ethereum", "solana", "ripple", "hyperliquid", "usd-coin", "tether");
    }
}
