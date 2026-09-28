package com.cryptoportfoliohub.connection.api;

import java.math.BigDecimal;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;

public record ConnectionPortfolioValueResponse(
        BigDecimal amountJpy,
        ConnectionPortfolioStatus status) {
}
