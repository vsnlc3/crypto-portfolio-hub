package com.cryptoportfoliohub.portfolio.application;

import java.time.Duration;

public enum PortfolioHistoryPeriod {
    SEVEN_DAYS("7D", Duration.ofDays(7)),
    THIRTY_DAYS("30D", Duration.ofDays(30)),
    NINETY_DAYS("90D", Duration.ofDays(90)),
    ONE_YEAR("1Y", Duration.ofDays(365));

    private final String value;
    private final Duration duration;

    PortfolioHistoryPeriod(String value, Duration duration) {
        this.value = value;
        this.duration = duration;
    }

    public String value() {
        return value;
    }

    public Duration duration() {
        return duration;
    }
}
