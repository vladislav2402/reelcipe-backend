package com.reelcipe.bootstrap;

public enum ApplicationRole {
    API,
    WORKER;

    public static ApplicationRole parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "app.role must be one of: api, worker; received: " + value, exception);
        }
    }
}
