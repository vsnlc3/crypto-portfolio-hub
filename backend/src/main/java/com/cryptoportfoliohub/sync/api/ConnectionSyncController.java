package com.cryptoportfoliohub.sync.api;

import com.cryptoportfoliohub.persistence.entity.SyncRun;
import com.cryptoportfoliohub.persistence.entity.SyncRunResult;
import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.security.CurrentUserService;
import com.cryptoportfoliohub.sync.application.ProviderSyncRegistry;
import com.cryptoportfoliohub.sync.application.SyncExecutionTicket;
import com.cryptoportfoliohub.sync.application.SyncRunQueryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionSyncController {

    private final ProviderSyncRegistry providerSyncRegistry;
    private final SyncRunQueryService syncRunQueryService;
    private final CurrentUserService currentUserService;

    public ConnectionSyncController(
            ProviderSyncRegistry providerSyncRegistry,
            SyncRunQueryService syncRunQueryService,
            CurrentUserService currentUserService) {
        this.providerSyncRegistry = providerSyncRegistry;
        this.syncRunQueryService = syncRunQueryService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{connectionId}/sync")
    public ResponseEntity<SyncAcceptedResponse> requestSync(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID connectionId) {
        User user = currentUserService.requireUser(principal);
        SyncExecutionTicket ticket = providerSyncRegistry.requestManualSync(user.getId(), connectionId);
        return ResponseEntity.accepted().body(new SyncAcceptedResponse(
                ticket.syncRunId(), ticket.connectionId(), ticket.triggerType(), SyncRunStatus.RUNNING,
                ticket.capabilities(), ticket.startedAt()));
    }

    @GetMapping("/{connectionId}/sync-runs/{syncRunId}")
    public SyncRunResponse getSyncRun(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID connectionId,
            @PathVariable UUID syncRunId) {
        User user = currentUserService.requireUser(principal);
        SyncRun run = syncRunQueryService.getOwnedRun(user.getId(), connectionId, syncRunId);
        var results = syncRunQueryService.getOwnedResults(user.getId(), connectionId, syncRunId).stream()
                .map(ConnectionSyncController::toResponse)
                .toList();
        return new SyncRunResponse(run.getId(), connectionId, run.getTriggerType(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), run.getErrorCategory(), run.getSafeErrorDetail(), results);
    }

    private static SyncRunCapabilityResponse toResponse(SyncRunResult result) {
        return new SyncRunCapabilityResponse(
                result.getId().getCapability(),
                result.getStatus(),
                result.getRecordsFetched(),
                result.getRecordsPersisted(),
                result.getStartedAt(),
                result.getFinishedAt(),
                result.getErrorCategory(),
                result.getSafeErrorDetail(),
                result.isContinuationAvailable());
    }
}
