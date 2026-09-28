package com.cryptoportfoliohub.activity.api;

public class ActivityCursorException extends RuntimeException {

    public ActivityCursorException() {
        super("The activity cursor is invalid.");
    }
}
