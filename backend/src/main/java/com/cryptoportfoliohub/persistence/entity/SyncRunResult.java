package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sync_run_results")
public class SyncRunResult {

    @EmbeddedId
    private SyncRunResultId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_run_id", referencedColumnName = "id", insertable = false, updatable = false)
    private SyncRun syncRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncResultStatus status;

    @Column(name = "records_fetched")
    private Integer recordsFetched;

    @Column(name = "records_persisted")
    private Integer recordsPersisted;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_category", length = 50)
    private String errorCategory;

    @Column(name = "safe_error_detail", length = 500)
    private String safeErrorDetail;

    @Column(name = "continuation_available", nullable = false)
    private boolean continuationAvailable;

    protected SyncRunResult() {
    }

    public SyncRunResult(
            SyncRun syncRun,
            SyncCapability capability,
            SyncResultStatus status,
            Integer recordsFetched,
            Integer recordsPersisted,
            Instant startedAt,
            Instant finishedAt,
            String errorCategory,
            String safeErrorDetail,
            boolean continuationAvailable) {
        this.id = new SyncRunResultId(syncRun.getId(), capability);
        this.syncRun = syncRun;
        this.status = status;
        this.recordsFetched = recordsFetched;
        this.recordsPersisted = recordsPersisted;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorCategory = errorCategory;
        this.safeErrorDetail = safeErrorDetail;
        this.continuationAvailable = continuationAvailable;
    }

    public SyncRunResultId getId() {
        return id;
    }

    public SyncRun getSyncRun() {
        return syncRun;
    }

    public SyncResultStatus getStatus() {
        return status;
    }

    public Integer getRecordsFetched() {
        return recordsFetched;
    }

    public Integer getRecordsPersisted() {
        return recordsPersisted;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public String getSafeErrorDetail() {
        return safeErrorDetail;
    }

    public boolean isContinuationAvailable() {
        return continuationAvailable;
    }
}
