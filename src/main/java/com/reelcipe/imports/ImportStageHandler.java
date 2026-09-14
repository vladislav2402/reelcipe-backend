package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportLease;

@FunctionalInterface
public interface ImportStageHandler {
    void handle(ImportLease lease, ImportLeaseControl control);
}
