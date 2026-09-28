package com.cryptoportfoliohub.sync.application;

import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.SyncRun;
import com.cryptoportfoliohub.persistence.repository.SyncRunRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunResultRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncRunQueryService {

    private final SyncRunRepository syncRunRepository;
    private final SyncRunResultRepository syncRunResultRepository;

    public SyncRunQueryService(
            SyncRunRepository syncRunRepository,
            SyncRunResultRepository syncRunResultRepository) {
        this.syncRunRepository = syncRunRepository;
        this.syncRunResultRepository = syncRunResultRepository;
    }

    @Transactional(readOnly = true)
    public SyncRun getOwnedRun(UUID authenticatedUserId, UUID connectionId, UUID syncRunId) {
        return syncRunRepository.findByIdAndConnection_IdAndConnection_User_Id(
                        syncRunId, connectionId, authenticatedUserId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.cryptoportfoliohub.persistence.entity.SyncRunResult> getOwnedResults(
            UUID authenticatedUserId, UUID connectionId, UUID syncRunId) {
        if (syncRunRepository.findByIdAndConnection_IdAndConnection_User_Id(
                syncRunId, connectionId, authenticatedUserId).isEmpty()) {
            throw new ResourceNotFoundException();
        }
        return syncRunResultRepository.findAllOwnedResults(syncRunId, connectionId, authenticatedUserId);
    }
}
