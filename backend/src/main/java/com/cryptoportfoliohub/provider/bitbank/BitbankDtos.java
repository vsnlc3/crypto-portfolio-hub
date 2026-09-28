package com.cryptoportfoliohub.provider.bitbank;

import java.math.BigDecimal;

final class BitbankDtos {

    private BitbankDtos() {
    }

    record Asset(String asset, BigDecimal freeAmount, BigDecimal onhandAmount,
            BigDecimal lockedAmount, BigDecimal withdrawingAmount, Integer amountPrecision) {
    }

    record SpotPair(String name, String baseAsset, String quoteAsset) {
    }

    record Trade(String tradeId, String pair, String side, String positionSide, String type,
            BigDecimal amount, BigDecimal price, BigDecimal feeAmountBase, BigDecimal feeAmountQuote,
            BigDecimal feeOccurredAmountQuote, long executedAtMillis) {
    }

    record Deposit(String uuid, String asset, String network, BigDecimal amount, String txid,
            String status, String category, long foundAtMillis, Long confirmedAtMillis) {
    }

    record Withdrawal(String uuid, String asset, BigDecimal amount, BigDecimal fee,
            String network, String txid, String status, long requestedAtMillis) {
    }
}
