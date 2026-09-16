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
    private final com.reelcipe.imports.transcription.TranscriptionPipelineService transcription;
    private final com.reelcipe.imports.recipe.RecipeExtractionPipelineService recipe;
    private final com.reelcipe.imports.recipe.RecipeValidationPipelineService validation;

    public ProcessingCopyStageHandler(
            ProcessingCopyService copies,
            com.reelcipe.imports.audio.AudioPipelineService audio,
            com.reelcipe.imports.transcription.TranscriptionPipelineService transcription,
            com.reelcipe.imports.recipe.RecipeExtractionPipelineService recipe,
            com.reelcipe.imports.recipe.RecipeValidationPipelineService validation) {
        this.copies = copies;
        this.audio = audio;
        this.transcription = transcription;
        this.recipe = recipe;
        this.validation = validation;
    }

    @Override
    public void handle(ImportLease lease, ImportLeaseControl control) {
        if (lease.stage() == ImportStage.RESOLVING) {
            copies.copy(lease, control);
        } else if (lease.stage() == ImportStage.EXTRACTING_AUDIO) {
            audio.extract(lease, control);
        } else if (lease.stage() == ImportStage.TRANSCRIBING) {
            transcription.transcribe(lease, control);
        } else if (lease.stage() == ImportStage.EXTRACTING_RECIPE) {
            recipe.extract(lease, control);
        } else if (lease.stage() == ImportStage.VALIDATING) {
            validation.validate(lease, control);
        }
    }
}
