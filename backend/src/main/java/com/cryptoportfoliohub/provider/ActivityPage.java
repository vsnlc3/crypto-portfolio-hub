package com.cryptoportfoliohub.provider;

import java.util.List;
import java.util.Objects;

public record ActivityPage(List<NormalizedActivity> activities, String nextCursor) {

    public ActivityPage {
        activities = List.copyOf(Objects.requireNonNull(activities, "activities must not be null"));
    }
}
