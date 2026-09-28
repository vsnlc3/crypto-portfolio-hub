package com.cryptoportfoliohub.connection.api;

import java.math.BigDecimal;
import com.cryptoportfoliohub.api.DecimalStringSerializer;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;
import tools.jackson.databind.annotation.JsonSerialize;

public record ConnectionPortfolioValueResponse(
        @JsonSerialize(using = DecimalStringSerializer.class)
        BigDecimal amountJpy,
        ConnectionPortfolioStatus status) {
}
