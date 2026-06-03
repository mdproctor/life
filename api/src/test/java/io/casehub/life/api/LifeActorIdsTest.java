package io.casehub.life.api;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifeActorIdsTest {

    private static final UUID ACTOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void ofProducesExpectedFormat() {
        assertThat(LifeActorIds.of(ACTOR_ID))
            .isEqualTo("life-actor:550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void isLifeActorReturnsTrueForValidPrefix() {
        assertThat(LifeActorIds.isLifeActor("life-actor:550e8400-e29b-41d4-a716-446655440000"))
            .isTrue();
    }

    @Test
    void isLifeActorReturnsFalseForOtherFormats() {
        assertThat(LifeActorIds.isLifeActor("claude:analyst@v1")).isFalse();
        assertThat(LifeActorIds.isLifeActor("life-system")).isFalse();
        assertThat(LifeActorIds.isLifeActor(null)).isFalse();
        assertThat(LifeActorIds.isLifeActor("")).isFalse();
    }

    @Test
    void extractIdRoundTrips() {
        String encoded = LifeActorIds.of(ACTOR_ID);
        assertThat(LifeActorIds.extractId(encoded)).isEqualTo(ACTOR_ID);
    }

    @Test
    void extractIdThrowsOnInvalidFormat() {
        assertThatThrownBy(() -> LifeActorIds.extractId("not-a-life-actor"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofThrowsOnNull() {
        assertThatThrownBy(() -> LifeActorIds.of(null))
            .isInstanceOf(NullPointerException.class);
    }
}
