package io.casehub.life.api;

public enum LifeCaseType {
    TRAVEL_PLAN("travel-plan", LifeDomain.TRAVEL),
    HOME_MAINTENANCE("home-maintenance", LifeDomain.HOUSEHOLD),
    CARE_COORDINATION("care-coordination", LifeDomain.ELDER_CARE),
    APPOINTMENT_CYCLE("appointment-cycle", LifeDomain.HEALTH),
    CONTRACTOR_COORDINATION("contractor-coordination", LifeDomain.CONTRACTOR_COORDINATION),
    FINANCIAL_REVIEW("financial-review", LifeDomain.FINANCE);

    private final String     caseName;
    private final LifeDomain domain;

    LifeCaseType(String caseName, LifeDomain domain) {
        this.caseName = caseName;
        this.domain   = domain;
    }

    public String caseName() {
        return caseName;
    }

    public LifeDomain domain() {
        return domain;
    }
}
