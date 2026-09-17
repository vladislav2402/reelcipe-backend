package com.reelcipe.providers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ProviderAdmissionRecovery {
    private final ProviderAdmissionService admissions;

    public ProviderAdmissionRecovery(ProviderAdmissionService admissions) {
        this.admissions = admissions;
    }

    @Scheduled(fixedDelayString = "${app.providers.admission.recovery-interval-ms:30000}")
    public void recover() {
        admissions.recoverExpired();
    }
}
