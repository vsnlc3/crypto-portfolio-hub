package com.cryptoportfoliohub.provider;

import java.time.Instant;

public interface ActivityProvider {
    ActivityPage fetchActivities(String accountAddress, String cursor, int limit, Instant fromInclusive);
}
