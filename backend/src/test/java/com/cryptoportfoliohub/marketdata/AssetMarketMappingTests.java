package com.cryptoportfoliohub.marketdata;

import org.junit.jupiter.api.Test;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import static org.assertj.core.api.Assertions.assertThat;

class AssetMarketMappingTests {

    @Test
    void mapsOnlyKnownProviderAssetIdentities() {
        assertThat(AssetMarketMapping.resolve("SOLANA:any", "SOLANA", AssetMarketMapping.SOLANA_USDC_MINT))
                .contains(new AssetMarketMapping.Identity("USDC", AssetCategory.STABLECOIN));
        assertThat(AssetMarketMapping.resolve("SOLANA:any", "SOLANA", AssetMarketMapping.SOLANA_USDT_MINT))
                .contains(new AssetMarketMapping.Identity("USDT", AssetCategory.STABLECOIN));
        assertThat(AssetMarketMapping.resolve("HYPERLIQUID:SPOT:0x6d1e7cde53ba9467b783cb7c530ce054",
                "HYPERLIQUID", AssetMarketMapping.HYPERLIQUID_USDC_TOKEN_ID))
                .contains(new AssetMarketMapping.Identity("USDC", AssetCategory.STABLECOIN));
        assertThat(AssetMarketMapping.resolve("HYPERLIQUID:SPOT:0x0d01dc56dcaaca66ad901c959b4011ec",
                "HYPERLIQUID", AssetMarketMapping.HYPERLIQUID_HYPE_TOKEN_ID))
                .contains(new AssetMarketMapping.Identity("HYPE", AssetCategory.CRYPTO));
        assertThat(AssetMarketMapping.resolve("SOLANA:fake-usdc", "SOLANA", "fake-usdc")).isEmpty();
        assertThat(AssetMarketMapping.resolve("HYPERLIQUID:SPOT:0xunknown", "HYPERLIQUID", "0xunknown"))
                .isEmpty();
    }

    @Test
    void mapsNativeSolAndBitbankCanonicalAssets() {
        assertThat(AssetMarketMapping.resolve("SOL", "SOLANA", "NATIVE"))
                .contains(new AssetMarketMapping.Identity("SOL", AssetCategory.CRYPTO));
        assertThat(AssetMarketMapping.resolve("BTC", "BITBANK", null))
                .contains(new AssetMarketMapping.Identity("BTC", AssetCategory.CRYPTO));
        assertThat(AssetMarketMapping.resolve("BTC", "OTHER", null)).isEmpty();
    }
}
