package com.cryptoportfoliohub.sync.application;

import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import java.util.List;
import java.util.Set;

public interface ProviderSyncPort {

    ConnectionProvider provider();

    Set<SyncCapability> capabilities();

    List<SyncCapabilityOutcome> synchronize(SyncExecutionTicket ticket);
}
