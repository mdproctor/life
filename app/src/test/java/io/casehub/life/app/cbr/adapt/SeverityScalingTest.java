package io.casehub.life.app.cbr.adapt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeverityScalingTest {

    @Test
    void highRatio_boostsByThree() {
        assertEquals(8, SeverityScaling.scale(3.0, 5.0, 5));
    }

    @Test
    void moderateRatio_boostsByOne() {
        assertEquals(6, SeverityScaling.scale(4.0, 5.0, 5));
    }

    @Test
    void noIncrease_keepsBase() {
        assertEquals(5, SeverityScaling.scale(5.0, 3.0, 5));
    }

    @Test
    void equalSeverity_keepsBase() {
        assertEquals(5, SeverityScaling.scale(5.0, 5.0, 5));
    }

    @Test
    void zeroRetrieved_keepsBase() {
        assertEquals(5, SeverityScaling.scale(0.0, 5.0, 5));
    }

    @Test
    void zeroCurrent_keepsBase() {
        assertEquals(5, SeverityScaling.scale(5.0, 0.0, 5));
    }

    @Test
    void negativeSeverity_keepsBase() {
        assertEquals(5, SeverityScaling.scale(-1.0, 5.0, 5));
    }

    @Test
    void cappedAtTen() {
        assertEquals(10, SeverityScaling.scale(1.0, 5.0, 9));
    }
}
