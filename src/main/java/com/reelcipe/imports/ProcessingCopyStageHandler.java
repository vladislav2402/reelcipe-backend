package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportLease;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ProcessingCopyStageHandler implements ImportStageHandler {
    private final ProcessingCopyService copies;

    public ProcessingCopyStageHandler(ProcessingCopyService copies) {
        this.copies = copies;
    }

    @Override
    public void handle(ImportLease lease, ImportLeaseControl control) {
        copies.copy(lease, control);
    }
}
