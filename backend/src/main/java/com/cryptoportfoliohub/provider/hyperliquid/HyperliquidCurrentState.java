package com.cryptoportfoliohub.provider.hyperliquid;

import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedPerpetualPosition;
import com.cryptoportfoliohub.provider.NormalizedProviderAccountState;
import java.util.List;

public record HyperliquidCurrentState(
        HyperliquidAccountMode accountMode,
        String providerAbstractionMode,
        boolean accountModeSupported,
        List<NormalizedAssetBalance> spotBalances,
        List<NormalizedProviderAccountState> accountStates,
        List<NormalizedPerpetualPosition> positions) {

    public HyperliquidCurrentState {
        spotBalances = List.copyOf(spotBalances);
        accountStates = List.copyOf(accountStates);
        positions = List.copyOf(positions);
    }
}
