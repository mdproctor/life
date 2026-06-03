package io.casehub.life.app.routing;

import io.casehub.platform.api.preferences.SingleValuePreference;
import java.util.Objects;

/**
 * Single-value preference wrapper for double values.
 * Used for trust routing configuration parameters (blend factors, quality floors).
 */
public record DoublePreference(double value) implements SingleValuePreference {

    public static DoublePreference of(double value) {
        return new DoublePreference(value);
    }

    public static DoublePreference parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        return new DoublePreference(Double.parseDouble(raw));
    }
}
