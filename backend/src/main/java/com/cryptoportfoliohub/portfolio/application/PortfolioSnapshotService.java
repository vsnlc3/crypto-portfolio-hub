package com.cryptoportfoliohub.portfolio.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.persistence.entity.PortfolioSnapshot;
import com.cryptoportfoliohub.persistence.entity.PortfolioSnapshotStatus;
import com.cryptoportfoliohub.persistence.repository.PortfolioSnapshotRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.portfolio.domain.PortfolioValuation;

@Service
public class PortfolioSnapshotService {

    private final PortfolioValuationService valuationService;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PortfolioSnapshotService(
            PortfolioValuationService valuationService,
            PortfolioSnapshotRepository snapshotRepository,
            UserRepository userRepository,
            Clock clock) {
        this.valuationService = valuationService;
        this.snapshotRepository = snapshotRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean createSnapshotIfEligible(UUID authenticatedUserId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        PortfolioValuation valuation = valuationService.valueUser(authenticatedUserId);
        if (!eligible(valuation)) {
            return false;
        }

        var user = userRepository.findByIdForUpdate(authenticatedUserId)
                .orElseThrow(ResourceNotFoundException::new);
        PortfolioSnapshotStatus status = valuation.snapshotFreshness() == DataFreshness.STALE
                ? PortfolioSnapshotStatus.STALE : PortfolioSnapshotStatus.COMPLETE;
        Instant dataAsOfAt = valuation.dataAsOfAt().orElseThrow();
        if (snapshotRepository.findFirstByUser_IdOrderBySnapshotAtDescIdDesc(authenticatedUserId)
                .filter(previous -> sameSnapshot(previous, valuation, dataAsOfAt, status))
                .isPresent()) {
            return false;
        }

        snapshotRepository.save(new PortfolioSnapshot(
                user,
                clock.instant(),
                dataAsOfAt,
                valuation.netWorthJpy().orElseThrow(),
                valuation.holdingsValueJpy().orElseThrow(),
                valuation.directionalValueJpy().orElseThrow(),
                valuation.stablecoinValueJpy().orElseThrow(),
                valuation.marketExposureJpy().orElseThrow(),
                valuation.unrealizedPnlJpy().orElseThrow(),
                status));
        return true;
    }

    private boolean eligible(PortfolioValuation valuation) {
        return valuation.hasSnapshotValues()
                && valuation.dataAsOfAt().isPresent()
                && (valuation.snapshotFreshness() == DataFreshness.FRESH
                        || valuation.snapshotFreshness() == DataFreshness.STALE);
    }

    private boolean sameSnapshot(
            PortfolioSnapshot previous,
            PortfolioValuation valuation,
            Instant dataAsOfAt,
            PortfolioSnapshotStatus status) {
        return previous.getDataAsOfAt().equals(dataAsOfAt)
                && previous.getStatus() == status
                && same(previous.getNetWorthJpy(), valuation.netWorthJpy().orElseThrow())
                && same(previous.getHoldingsValueJpy(), valuation.holdingsValueJpy().orElseThrow())
                && same(previous.getDirectionalValueJpy(), valuation.directionalValueJpy().orElseThrow())
                && same(previous.getStablecoinValueJpy(), valuation.stablecoinValueJpy().orElseThrow())
                && same(previous.getMarketExposureJpy(), valuation.marketExposureJpy().orElseThrow())
                && same(previous.getUnrealizedPnlJpy(), valuation.unrealizedPnlJpy().orElseThrow());
    }

    private boolean same(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) == 0;
    }
}
