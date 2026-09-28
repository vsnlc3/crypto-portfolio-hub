package com.cryptoportfoliohub.portfolio.domain;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record UserPortfolioValuation(
        PortfolioValuation portfolio,
        Map<UUID, ConnectionPortfolioValuation> connections) {

    public UserPortfolioValuation {
        Objects.requireNonNull(portfolio, "portfolio must not be null");
        connections = Map.copyOf(connections);
    }
}
