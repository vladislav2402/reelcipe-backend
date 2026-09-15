package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportLease;
import com.reelcipe.imports.domain.ImportStage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ProcessingCopyStageHandler implements ImportStageHandler {
    private final ProcessingCopyService copies;
    private final com.reelcipe.imports.audio.AudioPipelineService audio;

    public ProcessingCopyStageHandler(
            ProcessingCopyService copies,
            com.reelcipe.imports.audio.AudioPipelineService audio) {
        this.copies = copies;
        this.audio = audio;
    }

    @Override
    public void handle(ImportLease lease, ImportLeaseControl control) {
        if (lease.stage() == ImportStage.RESOLVING) {
            copies.copy(lease, control);
        } else if (lease.stage() == ImportStage.EXTRACTING_AUDIO) {
            audio.extract(lease, control);
        }
    }
}
