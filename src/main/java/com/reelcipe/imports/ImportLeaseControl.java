package com.reelcipe.imports;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ImportLeaseControl {
    private final AtomicBoolean valid = new AtomicBoolean(true);

    public boolean isValid() {
        return valid.get();
    }

    void markLost() {
        valid.set(false);
    }
}
