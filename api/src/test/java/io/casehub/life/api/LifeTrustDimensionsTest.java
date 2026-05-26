package io.casehub.life.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LifeTrustDimensionsTest {

    @Test
    void allConstantsNonNullAndNonBlank() throws IllegalAccessException {
        for (Field field : LifeTrustDimensions.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                String value = (String) field.get(null);
                assertThat(value)
                        .as("LifeTrustDimensions.%s", field.getName())
                        .isNotNull()
                        .isNotBlank();
            }
        }
    }

    @Test
    void allConstantsUnique() throws IllegalAccessException {
        List<String> values = new ArrayList<>();
        for (Field field : LifeTrustDimensions.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                values.add((String) field.get(null));
            }
        }
        Set<String> unique = new HashSet<>(values);
        assertThat(unique).hasSameSizeAs(values);
    }

    @Test
    void allExpectedDimensionsPresent() throws IllegalAccessException {
        List<String> values = new ArrayList<>();
        for (Field field : LifeTrustDimensions.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                values.add((String) field.get(null));
            }
        }
        assertThat(values).containsExactlyInAnyOrder(
                "deadline-reliability",
                "cost-accuracy",
                "factual-accuracy",
                "proactive-alerting"
        );
    }
}
