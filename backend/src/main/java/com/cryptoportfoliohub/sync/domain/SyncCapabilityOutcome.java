package com.cryptoportfoliohub.sync.domain;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncResultStatus;
import java.util.Objects;
import java.util.Optional;

public record SyncCapabilityOutcome(
        SyncCapability capability,
        SyncResultStatus status,
        Optional<Integer> recordsFetched,
        Optional<Integer> recordsPersisted,
        Optional<ProviderErrorCategory> errorCategory,
        boolean continuationAvailable) {

    public SyncCapabilityOutcome {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(recordsFetched, "recordsFetched must not be null");
        Objects.requireNonNull(recordsPersisted, "recordsPersisted must not be null");
        Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        recordsFetched.ifPresent(count -> requireNonNegative(count, "recordsFetched"));
        recordsPersisted.ifPresent(count -> requireNonNegative(count, "recordsPersisted"));
        if ((status == SyncResultStatus.FAILED) != errorCategory.isPresent()) {
            throw new IllegalArgumentException("Only failed outcomes must have an error category.");
        }
        if (continuationAvailable && status != SyncResultStatus.SUCCESS) {
            throw new IllegalArgumentException("Only successful outcomes can have a continuation.");
        }
    }

    public static SyncCapabilityOutcome success(SyncCapability capability, int fetched, int persisted) {
        return new SyncCapabilityOutcome(capability, SyncResultStatus.SUCCESS,
                Optional.of(fetched), Optional.of(persisted), Optional.empty(), false);
    }

    public static SyncCapabilityOutcome success(
            SyncCapability capability, int fetched, int persisted, boolean continuationAvailable) {
        return new SyncCapabilityOutcome(capability, SyncResultStatus.SUCCESS,
                Optional.of(fetched), Optional.of(persisted), Optional.empty(), continuationAvailable);
    }

    public static SyncCapabilityOutcome failed(SyncCapability capability, ProviderErrorCategory category) {
        return new SyncCapabilityOutcome(capability, SyncResultStatus.FAILED,
                Optional.empty(), Optional.empty(), Optional.of(category), false);
    }

    public static SyncCapabilityOutcome skipped(SyncCapability capability) {
        return new SyncCapabilityOutcome(capability, SyncResultStatus.SKIPPED,
                Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    private static void requireNonNegative(int count, String field) {
        if (count < 0) {
            throw new IllegalArgumentException(field + " must be non-negative.");
        }
    }
}
