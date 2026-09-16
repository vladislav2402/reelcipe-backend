package com.reelcipe.imports.recipe;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.domain.ImportLease;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class RecipeValidationPipelineService {
    private final RecipeFinalizationService finalization;

    public RecipeValidationPipelineService(RecipeFinalizationService finalization) {
        this.finalization = finalization;
    }

    public void validate(ImportLease lease, ImportLeaseControl control) {
        if (!control.isValid()) {
            throw new com.reelcipe.imports.domain.LeaseLostException(lease);
        }
        finalization.finalizeImport(lease);
    }
}
