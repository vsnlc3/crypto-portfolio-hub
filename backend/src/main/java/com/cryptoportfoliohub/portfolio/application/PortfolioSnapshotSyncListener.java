package com.cryptoportfoliohub.portfolio.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.cryptoportfoliohub.sync.application.PortfolioRelevantSyncCompleted;

@Component
public class PortfolioSnapshotSyncListener {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotSyncListener.class);

    private final PortfolioSnapshotService snapshotService;

    public PortfolioSnapshotSyncListener(PortfolioSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterPortfolioSync(PortfolioRelevantSyncCompleted event) {
        try {
            snapshotService.createSnapshotIfEligible(event.authenticatedUserId());
        } catch (RuntimeException exception) {
            log.warn("Portfolio snapshot evaluation failed after a completed sync.");
        }
    }
}
