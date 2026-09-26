package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;

final class DecimalEquality {

    private DecimalEquality() {
    }

    static boolean equal(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) == 0;
    }

    static int hashCode(BigDecimal value) {
        return value.stripTrailingZeros().hashCode();
    }
}
