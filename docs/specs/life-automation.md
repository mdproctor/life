# casehub-life: Household Automation Spec

**Status:** Design reference  
**Scope:** casehub-life module — household and personal life automation  
**Audience:** Contributors building on or integrating with the casehub-life module

---

## 1. Purpose and Motivation

### The Gap OpenClaw Alone Cannot Fill

OpenClaw is an excellent execution platform. It can call APIs, browse the web, send messages, and orchestrate multi-step workflows autonomously. What it cannot do is hold anyone — including itself — accountable. There is no commitments model, no SLA enforcement, no oversight gate, no tamper-evident audit trail of what was promised and whether it was delivered.

CaseHub is an accountability and governance layer. It models obligations, enforces deadlines, routes escalations, and records outcomes in ways that matter legally and clinically. What it does not do is reach out and interact with the world — it needs an execution capability.

The combination addresses what neither can do alone:

- **OpenClaw alone:** executes brilliantly; commits to nothing; forgets everything
- **CaseHub alone:** enforces accountability; cannot act autonomously; requires explicit case creation
- **OpenClaw + CaseHub:** autonomous execution with obligation tracking, SLA enforcement, oversight gates, and audit trails

### Why the Personal Life Domain Matters

Enterprise CaseHub consumers (devtown, clinical, aml) deal with regulated, high-stakes obligations at institutional scale. Households face the same structural problem at personal scale — and often with higher consequences for failure:

- A missed medication window for an elderly parent is not a SLA breach in a JIRA ticket; it is a health event
- A contractor who does not show up is not a project slip; it is a household in disarray
- A legal deadline missed is not a process failure; it may be irreversible
- A referral that falls through the cracks affects a real patient outcome, not a sprint velocity metric

In personal life domains, the person affected and the person managing the obligation are often the same individual — or a small family unit without institutional support staff. CaseHub's accountability layer matters more here, not less.

### Domains Where Accountability Is the Differentiator

The personal life domains where CaseHub adds material value over OpenClaw alone:

| Domain | What Can Go Wrong Without Accountability |
|--------|------------------------------------------|
| Health coordination | Follow-up falls through; referral never chased; lab result never acted on |
| Elder/family care | Medication timing drifts; carer handover silently fails |
| Legal/compliance | Renewal deadline missed; proof of action unavailable |
| Contractor coordination | External commitment untracked; no-show with no escalation |
| Financial governance | Major spend decision taken without oversight; no record of authorisation |

---

## 2. Use Case Domains

### 2.1 Health Coordination

**What it manages:** medication schedules, GP and specialist appointments, lab result follow-up, referral chains, prescription renewals.

**Why CaseHub:** health decisions have named obligors (the GP), hard deadlines (follow-up within 14 days of a test result), and downstream consequences if the chain breaks. An OpenClaw agent can chase a referral; only CaseHub can record that the GP committed to sending it, that the deadline was missed, and that an escalation was triggered.

**SLA:** 14-day follow-up as default for outstanding referrals and lab results.

**Obligor model:** the GP or specialist is the named obligor on a Commitment. If they are not a CaseHub principal (they will not be), they are an external human actor — see actor model spec.

**Oversight channel:** health decisions involving medication changes or referrals route to a designated oversight channel. The household-admin (or a designated health-proxy principal) is the required responder.

**Privacy:** health data must be domain-isolated. No cross-bleed to finance, household, or work agents — see actor model spec §4.

**Appropriate level:** Level 4. Tamper-evident record of health commitments and outcomes matters. A ledger that cannot be altered after the fact is the right backing for health governance.

---

### 2.2 Financial Governance

**What it manages:** oversight gates on major spend decisions; multi-account visibility aggregated from banking and investment platforms; financial agent trust scoring.

**What it does not manage:** day-to-day budgeting, expense tracking, or transaction categorisation. Those are bookkeeping problems; OpenClaw or dedicated finance tools handle them. CaseHub governs the decision cycle around significant financial commitments.

**Pattern:** OpenClaw aggregates account data via Open Banking skills and investment platform skills. CaseHub creates a WorkItem when a spend exceeds a threshold or a financial obligation comes due. The household governance rules (single-party or dual-party decision — see actor model spec §3) determine whether one principal's COMMAND is sufficient or whether M-of-N quorum is required.

**Trust scoring:** financial agents carry trust scores. An agent with a degraded trust score cannot authorise a major spend decision; the WorkItem escalates to a human principal.

**Privacy:** financial data must not be accessible to household agents reachable by junior household members.

**Appropriate level:** Level 4. Major financial decisions benefit from tamper-evident authorisation records.

---

### 2.3 Elder/Family Care

**What it manages:** medication administration (by time of day), carer handovers, care provider coordination across multiple sites, escalation when a carer does not confirm completion.

**Why multi-actor:** elder care typically involves multiple principals (family members sharing care responsibility), multiple external actors (paid carers, district nurses, GPs), and potentially multiple physical sites (home, day centre, hospital). None of these actors will have CaseHub accounts.

**SLA:** medication administration by a configurable daily deadline (default 8am). Watchdog fires if no DONE confirmation received.

**Escalation chain:** home-agent or health-agent → WhatsApp/SMS to carer (OpenClaw messaging skill) → if no response, WorkItem to household-admin oversight channel.

**GDPR:** elder care data falls under Article 17 (right to erasure). CaseMemoryStore facts about an elderly person must be erasable independently of case ledger entries. The ledger records what happened; the memory store holds ongoing facts about the person. Erasure applies to the latter.

**Structure:** multi-site (separate site entries for each location), multi-agent (care coordinator agent per site), multi-principal (family members as household-admin or household-member roles). Level 4 mandatory for this domain.

---

### 2.4 Legal and Compliance Cycles

**What it manages:** contract renewals (lease, insurance, subscriptions), tax filing cycles, visa renewals, professional licence renewals, statutory obligations.

**Why CaseHub:** hard deadlines with real legal consequences. Proof of action matters — not just that a document was filed, but that it was filed before a specific date, by an authorised principal, with a record that cannot be altered. The ledger provides that proof.

**Pattern:** CasePlanModel drives the renewal workflow. Each step (obtain documents, review, sign, file, confirm receipt) is a tracked task with a deadline. Steps that require external action (solicitor review, government portal submission) use OpenClaw execution with CaseHub obligation tracking.

**Obligors:** external (solicitor, accountant, government body). Same external actor pattern as contractor coordination.

**Appropriate level:** Level 3 minimum (audit trail), Level 4 where tamper-evidence of timing matters (visa, tax).

---

### 2.5 Household Task Management

**What it manages:** recurring household tasks (grocery ordering, cleaning schedules), home maintenance (annual boiler service, filter replacements), energy management (tariff switching, usage anomalies).

**Why CaseHub at this level:** for recurring tasks, the accountability value is lighter — the consequence of missing a grocery order is inconvenience, not health risk. However, having a consistent model means the same agent infrastructure handles simple and complex tasks. The distinction is how much overhead to apply.

**Examples:**
- Grocery ordering: SLA of Wednesday deadline for weekly shop; DONE confirmed by agent when order placed; escalation to household-member if missed
- Boiler service: annual schedule; WorkItem created 4 weeks before due; contractor booking workflow triggered (see §2.7)
- Energy management: utility skill monitors tariff window; HOME_ASSISTANT event triggers when off-peak starts; WorkItem if anomaly detected in consumption pattern

**Appropriate level:** Level 1 sufficient for simple recurring tasks. No ledger overhead needed for a grocery order. Level 2-3 for maintenance scheduling with external contractors.

---

### 2.6 Appointment Booking

**What it manages:** booking workflows for GP appointments, specialist referrals, service appointments, school meetings.

**Pattern:** multi-step workflow. OpenClaw checks availability (calendar skill, booking portal skill), proposes slots, waits for principal COMMAND to confirm, sends confirmation, sets reminder, creates Watchdog for day-before confirmation.

**SLA:** booking deadline set at case creation (e.g., "GP appointment within 5 working days of referral received"). Watchdog fires if booking not confirmed by deadline.

**Appropriate level:** Level 2 for routine appointments. Level 3 where the appointment is part of a clinical pathway or legal requirement.

---

### 2.7 Family Task Delegation

**What it manages:** tasks that can be delegated to household members (school pickup, package collection, household errands).

**Pattern:** COMMAND from household-admin creates a WorkItem assigned to household-member. RESPONSE due by a configurable deadline (e.g., 2pm for school pickup). Watchdog fires at deadline if no DONE received. Escalation: household-admin notified to arrange alternative.

**Junior access:** household-junior role can respond to tasks assigned to them (RESPONSE) and query their task list (QUERY), but cannot issue COMMAND or create new obligations.

**Appropriate level:** Level 2. Lightweight accountability without ledger overhead.

---

### 2.8 Travel Planning

**What it manages:** multi-step travel booking (flights, accommodation, transfers, visas, travel insurance), budget gate, itinerary consolidation.

**Pattern:** CasePlanModel drives the booking sequence. Budget gate: OpenClaw aggregates cost estimate; if total exceeds threshold, dual-party approval required before any booking is confirmed. Each booking step is a tracked commitment with confirmation document attached.

**External actors:** airlines, hotels, visa authorities — none have CaseHub accounts. All commitments are recorded with the household principal as the verifying party.

**Appropriate level:** Level 3. Travel commitments involve real financial exposure and time-sensitive deadlines (visa applications, check-in windows).

---

### 2.9 Contractor Coordination

**What it manages:** engagement, scheduling, and follow-up for external service providers (plumber, electrician, builder, cleaner, gardener).

**Why this is structurally different:** the contractor is an external human actor who makes a commitment (will arrive Thursday 10am) but has no CaseHub account and no ability to directly update a WorkItem. All accountability is proxy-tracked through the household principal and CaseHub's Watchdog mechanism.

**Pattern:** see actor model spec §2 for the full concrete workflow.

**Appropriate level:** Level 3-4. Where payment commitments are involved (deposit, staged payments), Level 4 provides tamper-evident authorisation records.

---

## 3. Progressive Adoption Gradient

Household automation does not require committing to the full CaseHub stack from day one. The adoption ladder allows incremental value at each level.

| Level | Name | What It Adds | Commitment Required |
|-------|------|--------------|---------------------|
| **0** | OpenClaw only | Autonomous execution; no accountability | OpenClaw deployment only |
| **1** | + casehub-engine | WorkItems, Watchdogs, COMMAND/RESPONSE routing | casehub-engine |
| **2** | + casehub-qhorus | Channels, oversight gates, escalation routing | + casehub-qhorus |
| **3** | + casehub-work | CasePlanModel, multi-step workflow, task sequencing | + casehub-work |
| **4** | + casehub-ledger | Tamper-evident audit trail, proof of action, legal-grade records | + casehub-ledger |

**CaseMemoryStore** is orthogonal to this ladder. It can be added at any level from Level 1 upward — it is not a prerequisite for any level and does not depend on the ledger. The Memori adapter requires Postgres, which is the same infrastructure already used by the JPA persistence backend. No additional infrastructure is needed to adopt CaseMemoryStore at Level 1.

**Practical guidance by domain:**

| Domain | Minimum Useful Level | Recommended Level |
|--------|----------------------|-------------------|
| Household tasks | Level 1 | Level 1-2 |
| Appointment booking | Level 2 | Level 2-3 |
| Family task delegation | Level 2 | Level 2 |
| Travel planning | Level 3 | Level 3 |
| Legal/compliance | Level 3 | Level 4 |
| Contractor coordination | Level 3 | Level 3-4 |
| Health coordination | Level 4 | Level 4 |
| Financial governance | Level 4 | Level 4 |
| Elder/family care | Level 4 | Level 4 |

---

## 4. OpenClaw Skill Value

### The Critical Distinction

Browser MCP gives OpenClaw the ability to navigate arbitrary websites. This is a valid fallback capability and useful for one-off tasks. It is not the primary value proposition of OpenClaw as a CaseHub execution partner.

The real value is **native platform skills** — pre-built, tested integrations with specific services that operate at the API level, not the browser DOM level. Native skills are faster, more reliable, cheaper to run (no browser overhead), and produce structured data that CaseHub can reason over.

Browser MCP as a compelling showcase use case is orthogonal to the OpenClaw skill maximisation strategy. Do not conflate them.

### Concrete Native Skill Scenarios

**Multi-account financial aggregation:**
- OpenClaw uses Open Banking skills (account balance, transaction history, direct debit schedule) and investment platform skills (portfolio value, pending dividends)
- CaseHub receives structured financial snapshot, creates WorkItem for the decision cycle
- Oversight gate: dual-party COMMAND required if spend exceeds threshold
- Ledger records the authorisation with timestamp and principal identity

**Smart home and health integration:**
- OpenClaw uses Fitbit/Apple Health skill (activity, sleep, heart rate anomalies) and Home Assistant skill (pill dispenser state, sensor readings)
- Pattern: dispenser lid not opened by 8am → health-agent reads sensor → CaseHub creates WorkItem (medication not confirmed)
- Escalation: home-agent sends WhatsApp message to carer via messaging skill
- No browser required; the entire chain operates through structured API integrations

**Calendar and contractor coordination:**
- OpenClaw uses Google Calendar skill (availability, existing commitments) and messaging skills (WhatsApp, SMS, email)
- Contractor commits to a time; CaseHub creates Commitment with Watchdog
- At deadline: calendar-agent checks if appointment was marked attended; if not, messaging skill sends confirmation chase
- Response (or non-response) feeds back into CaseHub WorkItem

**Energy monitoring and oversight:**
- OpenClaw uses utility skill (tariff data, consumption history) and Home Assistant skill (device energy readings, smart plug state)
- Heartbeat pattern: energy-agent posts periodic usage EVENT to observe channel
- Anomaly detected → EVENT to oversight channel → household-admin WorkItem for review
- Oversight gate: if agent recommends tariff switch, dual-party approval before any action taken

### When Browser MCP Is Appropriate

Browser MCP is appropriate when no native skill exists for a target service and the task is infrequent enough that the reliability overhead is acceptable. It is a valid fallback, not a first choice. For the OpenClaw + CaseHub showcase, native skills should be the primary demonstration.

---

## 5. Strategic Positioning

### Developer Showcase with Tutorial Layers

casehub-life follows the same developer showcase pattern as devtown and clinical:
- A module that demonstrates the platform with real-world use cases
- Tutorial layers structured to guide adoption incrementally
- Each tutorial layer adds one foundation module, starting from Naive Java with no accountability and building through Layers 1–7
- The comparison baseline throughout is OpenClaw alone — each tutorial step shows what CaseHub adds

### Consumer Product Direction

The consumer product direction (a packaged household automation product for non-developer end users) is not foreclosed by the developer showcase positioning. The two are compatible:
- Developer showcase establishes technical credibility and surfaces real integration patterns
- Consumer packaging, if pursued, builds on the same foundation modules with a different UX layer

This remains an open question from the design process. The developer showcase is decided; consumer product direction is not.

### Tutorial Layer Structure

| Tutorial Layer | Foundation Adopted | What the Tutorial Demonstrates |
|----------------|-------------------|-------------------------------|
| Layer 1 | Naive Java (no foundation) | ExternalActor entity, REST API — what accountability gaps look like without the platform |
| Layer 2 | + casehub-work | SLA enforcement: WorkItem + LifeTaskContext supplement, deadline escalation |
| Layer 3 | + casehub-qhorus | Commitment lifecycle: family delegation, contractor follow-up, oversight gates |
| Layer 4 | + casehub-ledger | Tamper-evident audit for health, financial, and legal decisions; GDPR Art.17 erasure |
| Layer 5 | + casehub-engine | Multi-step CasePlanModel workflows: travel, care coordination, home maintenance |
| Layer 6 | Trust routing | Trust-weighted agent routing from WorkItem outcomes and commitment attestations |
| Layer 7 | + casehub-openclaw | OpenClaw as WorkerProvisioner; full execution + accountability stack |

---

## Open Questions

- Consumer product direction: developer showcase decided; consumer packaging not yet resolved
- Quorum configuration for household decisions: CasePlanModel property, channel property, or life-specific config entity? (See actor model spec §3)
- Domain isolation implementation: permission model configuration or structural property of casehub-life domain model? (See actor model spec §4)
- External actor entity: ExternalActor in casehub-qhorus-api or life-specific? (See actor model spec §1)
