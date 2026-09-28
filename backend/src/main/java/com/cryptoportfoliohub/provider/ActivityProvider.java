package com.cryptoportfoliohub.provider;

public interface ActivityProvider {
    ActivityPage fetchActivities(String accountAddress, String cursor, int limit);
}
