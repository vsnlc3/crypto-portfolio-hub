package com.cryptoportfoliohub.provider;

import java.util.List;
import java.util.Objects;

public record ActivityPage(List<NormalizedActivity> activities, String nextCursor, boolean limitedByProviderHistory) {

    public ActivityPage(List<NormalizedActivity> activities, String nextCursor) {
        this(activities, nextCursor, false);
    }

    public ActivityPage {
        activities = List.copyOf(Objects.requireNonNull(activities, "activities must not be null"));
    }
}
