package com.reelcipe.imports.source;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportLease;

public interface SourceResolver {
    boolean supports(ImportJob job);

    boolean resolve(ImportLease lease, ImportLeaseControl control);
}
