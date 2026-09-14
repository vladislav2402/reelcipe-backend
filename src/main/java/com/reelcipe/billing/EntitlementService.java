package com.reelcipe.billing;

import com.reelcipe.billing.domain.QuotaPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EntitlementService {
    private final QuotaPlan localPlan;

    public EntitlementService(@Value("${app.entitlement.local-plan:FREE}") String localPlan) {
        this.localPlan = QuotaPlan.valueOf(localPlan.trim().toUpperCase());
    }

    public QuotaPlan currentPlan(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }
        return localPlan;
    }
}
