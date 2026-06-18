package io.casehub.life.app.engine.agent;

/**
 * Structured output schema for the book-appointment OpenClaw agent.
 *
 * <p>Used as the responseSchema for AgentBuilder — LangChain4J derives the JSON schema
 * from this record's components. Agent#execute() enforces structured output so OpenClaw must
 * return conforming JSON.
 *
 * <p>confirmed=false is the correct value for a PENDING booking. The confirm-appointment
 * binding fires when .booking != null and .booking.declined != true and .confirmation == null.
 */
public record BookingResult(
        String appointmentId,
        String provider,
        boolean confirmed,
        Boolean declined,
        String reason) {}
