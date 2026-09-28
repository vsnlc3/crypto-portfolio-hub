package com.cryptoportfoliohub.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.persistence.entity.PortfolioSnapshot;
import com.cryptoportfoliohub.persistence.entity.PortfolioSnapshotStatus;
import com.cryptoportfoliohub.persistence.repository.PortfolioSnapshotRepository;
import com.cryptoportfoliohub.portfolio.api.PortfolioHistoryResponse;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryResponse;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryStatus;

@Service
public class PortfolioHistoryQueryService {

    private static final Duration COMPARISON_PERIOD = Duration.ofHours(24);
    private static final int PERCENT_SCALE = 8;

    private final PortfolioSnapshotRepository snapshotRepository;
    private final Clock clock;

    public PortfolioHistoryQueryService(PortfolioSnapshotRepository snapshotRepository, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PortfolioHistoryResponse getHistory(UUID authenticatedUserId, PortfolioHistoryPeriod period) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        Instant rangeEndAt = clock.instant();
        Instant rangeStartAt = rangeEndAt.minus(period.duration());
        List<PortfolioSnapshot> snapshots = snapshotRepository
                .findAllByUser_IdAndSnapshotAtGreaterThanEqualAndSnapshotAtLessThanEqualOrderBySnapshotAtAscIdAsc(
                        authenticatedUserId, rangeStartAt, rangeEndAt);
        List<PortfolioHistoryResponse.Point> points = snapshots.stream()
                .map(snapshot -> new PortfolioHistoryResponse.Point(
                        snapshot.getSnapshotAt(),
                        snapshot.getDataAsOfAt(),
                        snapshot.getNetWorthJpy(),
                        snapshot.getStatus()))
                .toList();
        return new PortfolioHistoryResponse(
                period.value(),
                points.isEmpty() ? PortfolioHistoryResponse.Status.EMPTY : PortfolioHistoryResponse.Status.AVAILABLE,
                rangeStartAt,
                rangeEndAt,
                points);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse.Change24h getChange24h(UUID authenticatedUserId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        Instant now = clock.instant();
        PortfolioSnapshot current = snapshotRepository
                .findFirstByUser_IdAndSnapshotAtLessThanEqualOrderBySnapshotAtDescIdDesc(authenticatedUserId, now)
                .orElse(null);
        if (current == null) {
            return unavailableChange();
        }

        Instant comparisonCutoff = current.getSnapshotAt().minus(COMPARISON_PERIOD);
        PortfolioSnapshot baseline = snapshotRepository
                .findFirstByUser_IdAndSnapshotAtLessThanEqualOrderBySnapshotAtDescIdDesc(
                        authenticatedUserId, comparisonCutoff)
                .orElse(null);
        if (baseline == null) {
            return unavailableChange();
        }

        BigDecimal amountJpy = current.getNetWorthJpy().subtract(baseline.getNetWorthJpy());
        BigDecimal percentage = baseline.getNetWorthJpy().signum() <= 0
                ? null
                : amountJpy.multiply(BigDecimal.valueOf(100))
                        .divide(baseline.getNetWorthJpy(), PERCENT_SCALE, RoundingMode.HALF_UP);
        PortfolioSummaryStatus status = current.getStatus() == PortfolioSnapshotStatus.STALE
                        || baseline.getStatus() == PortfolioSnapshotStatus.STALE
                ? PortfolioSummaryStatus.STALE
                : PortfolioSummaryStatus.COMPLETE;
        return new PortfolioSummaryResponse.Change24h(
                amountJpy,
                percentage,
                status,
                baseline.getSnapshotAt(),
                current.getSnapshotAt());
    }

    private PortfolioSummaryResponse.Change24h unavailableChange() {
        return new PortfolioSummaryResponse.Change24h(
                null, null, PortfolioSummaryStatus.UNAVAILABLE, null, null);
    }
}
