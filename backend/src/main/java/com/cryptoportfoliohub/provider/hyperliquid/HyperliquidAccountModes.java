package com.cryptoportfoliohub.provider.hyperliquid;

import java.util.Set;

public final class HyperliquidAccountModes {

    private static final Set<String> KNOWN_RAW_VALUES = Set.of(
            "disabled", "unifiedAccount", "portfolioMargin", "default", "dexAbstraction");

    private HyperliquidAccountModes() {
    }

    public static Mapping map(String providerValue) {
        if (providerValue == null || providerValue.isBlank()) {
            return new Mapping(HyperliquidAccountMode.UNKNOWN, null);
        }
        return switch (providerValue) {
            // `disabled` means abstraction is disabled. Mapping it to Standard is an inference from
            // the official mode-setting values and the separate-balance Standard mode definition.
            case "disabled" -> new Mapping(HyperliquidAccountMode.STANDARD, "disabled");
            case "unifiedAccount" -> new Mapping(HyperliquidAccountMode.UNIFIED_ACCOUNT, providerValue);
            case "portfolioMargin" -> new Mapping(HyperliquidAccountMode.PORTFOLIO_MARGIN, providerValue);
            case "default", "dexAbstraction" -> new Mapping(HyperliquidAccountMode.UNSUPPORTED, providerValue);
            default -> new Mapping(HyperliquidAccountMode.UNKNOWN,
                    KNOWN_RAW_VALUES.contains(providerValue) ? providerValue : "UNKNOWN");
        };
    }

    public record Mapping(HyperliquidAccountMode mode, String providerValue) {
    }
}
