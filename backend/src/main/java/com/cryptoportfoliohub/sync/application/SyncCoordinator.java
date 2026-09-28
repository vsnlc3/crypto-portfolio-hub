package com.cryptoportfoliohub.sync.application;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import java.util.List;
import java.util.UUID;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
public class SyncCoordinator {

    private final SyncLifecycleService lifecycleService;
    private final TaskExecutor syncTaskExecutor;

    public SyncCoordinator(SyncLifecycleService lifecycleService, TaskExecutor syncTaskExecutor) {
        this.lifecycleService = lifecycleService;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    public SyncExecutionTicket requestSync(
            UUID authenticatedUserId,
            UUID connectionId,
            SyncTriggerType triggerType,
            ProviderSyncPort providerSyncPort) {
        SyncExecutionTicket ticket = lifecycleService.begin(
                authenticatedUserId,
                connectionId,
                triggerType,
                providerSyncPort.capabilities(),
                providerSyncPort.provider());
        try {
            syncTaskExecutor.execute(() -> execute(ticket, providerSyncPort));
        } catch (TaskRejectedException exception) {
            lifecycleService.finish(ticket, failedOutcomes(ticket, ProviderErrorCategory.UNAVAILABLE));
        }
        return ticket;
    }

    private void execute(SyncExecutionTicket ticket, ProviderSyncPort providerSyncPort) {
        List<SyncCapabilityOutcome> outcomes;
        try {
            outcomes = providerSyncPort.synchronize(ticket);
        } catch (ProviderException exception) {
            outcomes = failedOutcomes(ticket, exception.category());
        } catch (RuntimeException exception) {
            outcomes = failedOutcomes(ticket, ProviderErrorCategory.UNAVAILABLE);
        }
        lifecycleService.finish(ticket, outcomes);
    }

    private List<SyncCapabilityOutcome> failedOutcomes(
            SyncExecutionTicket ticket, ProviderErrorCategory category) {
        return ticket.capabilities().stream()
                .map(capability -> SyncCapabilityOutcome.failed(capability, category))
                .toList();
    }
}
