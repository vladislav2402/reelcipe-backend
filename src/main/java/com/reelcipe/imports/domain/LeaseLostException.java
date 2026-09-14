package com.reelcipe.imports.domain;

public class LeaseLostException extends RuntimeException {
    public LeaseLostException(ImportLease lease) {
        super("Import lease is no longer valid: " + lease.importId());
    }
}
