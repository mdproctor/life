package io.casehub.life.app.commitment;

/**
 * Sealed context hierarchy for LifeCommitmentStrategy dispatch.
 * Each subtype maps to exactly one strategy — no nullable mode fields.
 * All permitted subtypes are top-level records in this package.
 */
public sealed interface CommitmentContext
        permits DelegationContext, ContractorContext, OversightContext {
}
