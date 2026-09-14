package com.reelcipe.billing.domain;

public enum QuotaPlan {
    FREE(10),
    PRO(100);

    private final int monthlyLimit;

    QuotaPlan(int monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public int monthlyLimit() {
        return monthlyLimit;
    }
}
