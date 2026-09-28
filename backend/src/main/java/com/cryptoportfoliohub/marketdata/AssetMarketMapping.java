package com.cryptoportfoliohub.marketdata;

import java.util.Optional;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;

/** Resolves provider-specific identities to supported market-data assets. */
public final class AssetMarketMapping {

    public static final String SOLANA_USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    public static final String SOLANA_USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";
    public static final String HYPERLIQUID_USDC_TOKEN_ID = "0x6d1e7cde53ba9467b783cb7c530ce054";
    public static final String HYPERLIQUID_HYPE_TOKEN_ID = "0x0d01dc56dcaaca66ad901c959b4011ec";

    private AssetMarketMapping() {
    }

    public static Optional<Identity> resolve(String assetKey, String network, String assetRef) {
        if (assetKey == null || network == null) {
            return Optional.empty();
        }
        if ("SOLANA".equals(network)) {
            if ("NATIVE".equals(assetRef) && "SOL".equals(assetKey)) {
                return Optional.of(new Identity("SOL", AssetCategory.CRYPTO));
            }
            if (SOLANA_USDC_MINT.equals(assetRef)) {
                return Optional.of(new Identity("USDC", AssetCategory.STABLECOIN));
            }
            if (SOLANA_USDT_MINT.equals(assetRef)) {
                return Optional.of(new Identity("USDT", AssetCategory.STABLECOIN));
            }
            return Optional.empty();
        }
        if ("HYPERLIQUID".equals(network)) {
            if (HYPERLIQUID_USDC_TOKEN_ID.equalsIgnoreCase(assetRef)) {
                return Optional.of(new Identity("USDC", AssetCategory.STABLECOIN));
            }
            if (HYPERLIQUID_HYPE_TOKEN_ID.equalsIgnoreCase(assetRef)) {
                return Optional.of(new Identity("HYPE", AssetCategory.CRYPTO));
            }
            return Optional.empty();
        }
        if ("BITBANK".equals(network) && CoinGeckoAssetMapping.coinIdFor(assetKey).isPresent()) {
            AssetCategory category = "USDC".equals(assetKey) || "USDT".equals(assetKey)
                    ? AssetCategory.STABLECOIN : AssetCategory.CRYPTO;
            return Optional.of(new Identity(assetKey, category));
        }
        return Optional.empty();
    }

    public record Identity(String canonicalAssetKey, AssetCategory category) {
        public Identity {
            if (canonicalAssetKey == null || canonicalAssetKey.isBlank() || category == null) {
                throw new IllegalArgumentException("A canonical asset identity is required.");
            }
        }
    }
}
