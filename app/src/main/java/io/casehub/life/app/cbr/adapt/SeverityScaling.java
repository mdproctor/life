package io.casehub.life.app.cbr.adapt;

public final class SeverityScaling {

    private SeverityScaling() {}

    public static int scale(double retrievedSeverity, double currentSeverity, int basePriority) {
        if (currentSeverity <= 0 || retrievedSeverity <= 0) return basePriority;
        double ratio = currentSeverity / retrievedSeverity;
        if (ratio > 1.5) return Math.min(basePriority + 3, 10);
        if (ratio > 1.0) return Math.min(basePriority + 1, 10);
        return basePriority;
    }
}
