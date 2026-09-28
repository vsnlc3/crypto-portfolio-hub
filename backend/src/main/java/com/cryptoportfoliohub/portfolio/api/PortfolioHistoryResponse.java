package com.cryptoportfoliohub.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.cryptoportfoliohub.persistence.entity.PortfolioSnapshotStatus;

public record PortfolioHistoryResponse(
        String period,
        Status status,
        Instant rangeStartAt,
        Instant rangeEndAt,
        List<Point> points) {

    public PortfolioHistoryResponse {
        points = List.copyOf(points);
    }

    public enum Status {
        AVAILABLE,
        EMPTY
    }

    public record Point(
            Instant snapshotAt,
            Instant dataAsOfAt,
            BigDecimal netWorthJpy,
            PortfolioSnapshotStatus status) {
    }
}
