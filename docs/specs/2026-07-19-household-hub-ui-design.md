# Household Hub — UI & Interaction Layer Design

**Date:** 2026-07-19
**Status:** Draft
**Scope:** Full vision with phased implementation (Arc42Stories chapters)

## 1. Product Concept

**Household Hub** — a single web application where 3–5 household members go for
all their life coordination needs. Structured views for observability and action;
embedded conversational UI for human-in-the-loop interaction; ambient intake from
the communication channels people already use.

The system is not a replacement for Google Calendar, Contacts, or task apps — it is
a **coordination and accountability layer** over the ecosystems people already have.
It owns the rich domain model (trust scores, SLA enforcement, commitments, audit
trail, CBR-informed decisions); external systems provide the day-to-day interaction
surface.

## 2. Users & Roles

| Role | Sees | Can do |
|------|------|--------|
| `household-admin` | Everything — all domains, all members' tasks, all cases | Approve financial decisions, configure SLAs, delegate tasks, manage external actors |
| `household-member` | All domains, all tasks, all cases | Action assigned tasks, request new tasks, respond to delegations |
| `household-junior` | Own tasks only | Complete assigned tasks, view own case participation |

Each member has their own login (OIDC, already wired via life#40). Views are
role-filtered via `CurrentPrincipal` and `LifeTaskVisibilityPolicy`.

## 3. Design Principles

**P1 — Own the domain model, project into external systems.**
Life maintains canonical rich records (ExternalActor with trust, WorkItem with SLA,
cases with CBR). External systems (Google Contacts, Calendar, Tasks) receive
projected views using fields they support. Changes reconcile bidirectionally. The
external system is a viewport; life is the source of truth for accountability.

**P2 — Ship standalone, integrate for production.**
Demo mode ships pre-populated with realistic data — no external accounts needed.
Production mode syncs with the user's existing ecosystem. Both modes use the same
domain model and UI; only the data source layer differs.

**P3 — Compose from blocks-ui.**
Views are compositions of reusable blocks-ui Web Components wired to REST/SSE
endpoints. New components are built in blocks-ui (not locally) and consumed via
`@casehubio/blocks-ui-*` packages. Life-specific compositions live in life-ui.

**P4 — Conversational for interaction, structured for observation.**
Dashboards, inboxes, and reports are structured views — you scan them visually.
When the system needs a human decision or a human wants to do something ("order
groceries", "find me a plumber"), a conversational pane handles it with context
pre-loaded. Implementation: Claudony embedding if the embedding API materialises
(§8); fallback: direct Claude API integration or xterm.js terminal (§8.3).

**P5 — Intelligence at the seams.**
Neocortex CBR + memory informs ambient intake classification, routing decisions,
case calibration, and conversational context. The UI surfaces this as similarity
panels, routing rationale, trust scores, and CBR-informed suggestions — not as a
black box.

## 4. Architecture

### 4.1 System Context

```
┌─────────────────────────────────────────────────────────┐
│                    Household Hub                         │
│                  (casehub-life-ui)                       │
│                                                         │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌────────────┐ │
│  │   Home   │ │  Inbox   │ │ People  │ │   Cases    │ │
│  │Dashboard │ │  + Chat  │ │Contacts │ │            │ │
│  └────┬─────┘ └────┬─────┘ └────┬────┘ └─────┬──────┘ │
│       │             │            │             │        │
│  ┌────┴─────┐       │            │             │        │
│  │ Journal  │       │            │             │        │
│  │ Reports  │       │            │             │        │
│  └────┬─────┘       │            │             │        │
│       └─────────────┴────────────┴─────────────┘        │
│                         │                               │
│                 blocks-ui components                    │
│                         │                               │
│          ┌──────────────┼───────────────┐               │
│          │              │               │               │
│      REST APIs    SSE streams     Claudony              │
└──────────┼──────────────┼───────────────┼───────────────┘
           │              │               │
┌──────────┴──────────────┴───────────────┴───────────────┐
│                 casehub-life backend                     │
│                                                         │
│  Existing REST ─────────────────────────────────────┐   │
│  (18 endpoints)                                     │   │
│                                                     │   │
│  ┌─────────────────────────────────────────────┐    │   │
│  │          Ambient Intake Pipeline             │    │   │
│  │                                             │    │   │
│  │  WhatsApp ──┐                               │    │   │
│  │  Email ─────┼──→ neocortex ──→ cases/tasks  │    │   │
│  │  Calendar ──┘    (CBR + memory routing)      │    │   │
│  └─────────────────────────────────────────────┘    │   │
│                                                     │   │
│  ┌─────────────────────────────────────────────┐    │   │
│  │        External System Sync Layer            │    │   │
│  │                                             │    │   │
│  │  Google Contacts ←→ ExternalActor           │    │   │
│  │  Google Calendar ←→ WorkItem deadlines      │    │   │
│  │  Google Tasks    ←→ WorkItem projected view │    │   │
│  └─────────────────────────────────────────────┘    │   │
│                                                     │   │
│  ┌─────────────────────────────────────────────┐    │   │
│  │           IoT (embedded / linked)            │    │   │
│  │  casehub-iot webapp endpoints               │    │   │
│  └─────────────────────────────────────────────┘    │   │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend framework | Lit 3.x (Web Components) |
| Component library | blocks-ui (`@casehubio/blocks-ui-*`) |
| Design tokens | `@casehubio/pages-ui-tokens` (OKLCH scales, spacing, typography) |
| Build / dev server | Vite (via Quinoa) |
| Backend serving | Quarkus + Quinoa |
| Real-time | SSE via Quarkus RESTEasy Reactive (`Multi<OutboundSseEvent>`); consumed by `EventStreamController` (blocks-ui core) |
| Conversational UI | Claudony if embedding API viable; fallback: direct Claude API or xterm.js (Phase 2) |
| Auth | OIDC (`casehub-platform-oidc`, already wired) |
| Data fetching | `DataSourceMixin` / `fetchSource` (blocks-ui core) |

### 4.3 Module Structure

```
life/
├── api/          — casehub-life-api (pure Java, unchanged)
├── app/          — casehub-life (Quarkus backend + Quinoa frontend serving)
│   └── pom.xml   — adds quarkus-quinoa plugin with ui-dir=../life-ui
└── life-ui/      — frontend source (NOT a Maven module — a directory)
    ├── src/
    │   ├── shell/        — app shell, routing, nav, theme
    │   ├── views/        — page-level compositions (home, inbox, people, cases, journal)
    │   └── adapters/     — endpoint wiring, SSE subscriptions, conversational bridge
    ├── index.html
    ├── vite.config.ts
    └── package.json      — depends on @casehubio/blocks-ui-* packages
```

**Build integration:** Quinoa plugin in `app/pom.xml` with `quarkus.quinoa.ui-dir=../life-ui`.
`mvn package -pl app` invokes Quinoa, which runs `npm install && npm run build` in `life-ui/`,
then packages the Vite build output into the Quarkus application jar. Single deployment artifact.

**Dev workflow:** `mvn quarkus:dev -pl app` starts both Quarkus backend and Vite dev server
(via Quinoa). Vite serves the frontend with HMR; API calls proxy to the Quarkus backend on
the same port. No separate frontend dev process needed.

**Why not a third Maven module:** `life-ui/` has no Java code and produces no jar. Making it
a Maven module adds POM ceremony with no build benefit — Quinoa's `ui-dir` reference is
simpler and matches the Quarkus convention for non-Java frontend assets.

## 5. Views

### 5.1 Home / Dashboard

The landing page. At-a-glance household health, personalised by role.

| Zone | Component | Data Source |
|------|-----------|------------|
| KPI strip | `<kpi-metric-row>` | `/analytics/cases`, `/analytics/sla`, `/analytics/trust` |
| My pending (count + top 3) | `<work-item-inbox>` compact | `/pending-actions?candidateGroup={role}` |
| Active cases by domain | `<grouped-data-view>` | `GET /life-cases` (new) |
| SLA warnings | `<sla-indicator>` + `<sla-breach-policy>` | SSE breach events |
| Calendar preview | `<calendar-strip>` (new) | Google Calendar API / demo data |
| IoT house status | Embedded IoT panel | casehub-iot webapp endpoints |
| Notifications | `<notification-bell>` in header | SSE notification stream |

KPI examples: open tasks, SLA compliance %, active cases, pending approvals,
trust score averages, overdue items.

Admin sees household-wide KPIs. Member sees all domains (dashboard highlights
tasks assigned to them). Junior sees their assignment count.

### 5.2 Inbox

Personal task inbox — "what needs my attention right now."

Built on `<work-item-workbench>` (split-pane layout already exists in blocks-ui).

**Left pane — inbox list:**

| Feature | Component |
|---------|-----------|
| Three-tab perspective | `<work-item-inbox>` (My Work / Claimable / All) |
| Domain filter pills | `QueuePillBar` (built into inbox) |
| Urgency sorting | Due-soon items surface first |
| SSE live updates | `EventStreamController` |

**Right pane — detail + action:**

| Feature | Component |
|---------|-----------|
| Item detail | `<work-item-detail>` |
| Action bar | `DetailActionBar` (claim, complete, delegate) |
| Approval gate | `<approval-gate>` for oversight/risk decisions |
| SLA countdown | `<sla-indicator>` |
| Similar past cases | `<similarity-panel>` (CBR) |
| Activity timeline | `<blocks-timeline>` |
| Trust profile | `<trust-score-panel>` (if external actor involved) |
| Routing rationale | `<routing-rationale>` |
| Channel context | `<channel-activity>` (relevant qhorus channel messages) |

**Claudony integration:** "Respond" button on oversight gates, family votes,
and delegation requests opens the conversational pane with decision context
pre-loaded. The user talks through the decision; Claudony executes the
appropriate API call (RESPONSE, DONE, DECLINE).

### 5.3 People & Contacts

External actors — contractors, doctors, service providers, institutions.

**Integration model:** In production, syncs bidirectionally with Google Contacts.
Life enriches with trust scores, case history, capability tags, GDPR state.
In demo mode, ships pre-populated.

| Zone | Component | Data Source |
|------|-----------|------------|
| Contact list | `<list-pane>` with search/filter | `/external-actors` |
| Contact detail | `<detail-pane>` (tabbed) | `/external-actors/{id}` |
| — Trust tab | `<trust-score-panel>` | Trust data on actor response |
| — Activity tab | `<blocks-timeline>` | `/external-actors/{id}/activity` |
| — Trust history tab | `<trust-score-panel>` trend | `/external-actors/{id}/trust-history` |
| — Tasks tab | `<list-pane>` | `/external-actors/{id}/tasks` |
| — GDPR tab | `<gdpr-erasure-action>` | `DELETE .../personal-data` |

**ExternalActor ↔ Google Contacts mapping:**

| Life field | Google Contacts field | Sync direction |
|-----------|----------------------|----------------|
| name | name | bidirectional |
| contactMethod + contactValue | phone / email | bidirectional |
| actorType | group label | life → Google |
| trust scores | (not synced) | life only |
| case history | (not synced) | life only |
| GDPR erasure state | (not synced) | life only |
| notes link | notes field (URL back to life) | life → Google |

### 5.4 Cases

Active and historical case visibility across all 6 top-level case types.
SubCases (family-vote, care-episode) surface as nested items within their
parent case's timeline/detail view — they are not independently startable.

| Zone | Component | Data Source |
|------|-----------|------------|
| Case list by domain | `<grouped-data-view>` | `GET /life-cases` (new) |
| Case detail | `<detail-pane>` (tabbed) | `GET /life-cases/{id}` (new) |
| — Timeline tab | `<blocks-timeline>` (state progression) | Case lifecycle events |
| — Workers tab | `<agent-activity-panel>` (new) | Sentinel reports, worker decisions |
| — Routing tab | `<routing-rationale>` | Trust routing data |
| — Audit tab | `<audit-trail-viewer>` | Ledger entries for this case |
| — CBR tab | `<similarity-panel>` | CBR retrieval for this case type |
| — Commitments tab | `<commitment-lifecycle>` (blocks-ui #55) | Qhorus commitment state |
| — Channel tab | `<channel-activity>` | Relevant qhorus channels |

Case list supports filters: domain, status (active/completed/failed), case type,
date range. Cards show case type icon, domain, status, SLA state, assigned actors.

### 5.5 Journal & Reports

Retrospective view — what happened, trends, compliance. Not real-time; designed
for periodic review by household-admin.

| Zone | Component | Data Source |
|------|-----------|------------|
| Decision log | `<audit-trail-viewer>` (filtered) | Ledger entries by type/date |
| SLA compliance | `<kpi-metric-row>` + `<compliance-summary>` | `/analytics/sla` |
| Trust trends | `<trust-score-panel>` trend mode | `/analytics/trust` |
| Spend tracking | `<kpi-metric-row>` | Financial analytics (new) |
| Domain breakdown | `<grouped-data-view>` | Per-domain case/task stats |
| Case outcomes | `<grouped-data-view>` | Historical case completion data |

Reports are filterable by date range and domain. Admin sees full household.
Member sees all domains. Junior sees own task completion.

### 5.6 App Shell

The layout wrapper for all views.

| Element | Component / Pattern |
|---------|-------------------|
| Top navigation | Home / Inbox / People / Cases / Journal — route links |
| Notification bell | `<notification-bell>` — real-time SSE, badge count |
| User identity | Role indicator, member name, logout |
| Conversational pane | Slide-out right panel, always available, context-aware (Phase 2 — Claudony if embedding API viable; fallback: direct Claude API) |
| Theme | Light/dark toggle via `generateThemeCSS()` |
| Density | Comfortable/compact via design tokens |
| IoT link | Nav item or embedded panel (configurable) |

Keyboard shortcuts (from `<work-item-workbench>` pattern): `?` for help overlay,
`g h` go home, `g i` go inbox, `/` focus search.

## 6. Ambient Intake Pipeline

> **Scope note:** This section is a product-context summary. Ambient intake is a
> Phase 4 deliverable requiring its own detailed design spec before implementation
> — covering neocortex integration, classification taxonomy, message parsing, and
> routing logic. Included here to show the full data flow into the UI.

Server-side pipeline that listens to the household's communication channels and
creates structure from unstructured messages.

### 6.1 Inbound Sources

| Source | Mechanism | What it captures |
|--------|-----------|-----------------|
| WhatsApp | WhatsApp Business API webhook | Contractor messages, appointment confirmations, school notices, family coordination |
| Email (Gmail) | Gmail API push notifications | Solicitor deadlines, medical results, invoices, booking confirmations |
| Google Calendar | Calendar API watch | New events, changes, cancellations |

### 6.2 Processing Pipeline

```
Inbound message
      │
      ▼
  ┌────────────┐
  │  Classify   │ ← neocortex memory: "is this from a known actor?"
  │  (domain,   │ ← CBR retrieval: "similar messages → what action?"
  │   intent,   │ ← LLM classification when ambiguous
  │   urgency)  │
  └──────┬─────┘
         │
         ▼
  ┌────────────┐
  │   Route     │ ← trust routing: "which agent/person handles this?"
  │             │ ← risk classification: "does this need approval?"
  └──────┬─────┘
         │
         ▼
  ┌────────────┐
  │   Create    │ → WorkItem (with SLA, domain, external actor link)
  │             │ → Case (if pattern matches a case type)
  │             │ → Calendar entry (synced to Google Calendar)
  │             │ → Notification (SSE → inbox)
  └────────────┘
```

### 6.3 Examples

| Inbound | Classification | Action |
|---------|---------------|--------|
| WhatsApp from plumber: "I'll come Thursday 2pm" | contractor-coordination, APPOINTMENT | Calendar entry + update contractor case + notification |
| Email from school: "Parent-teacher meeting Mar 15" | family-scheduling, EVENT | Calendar entry + family task + notification to all members |
| Gmail from solicitor: "Please respond by June 30" | legal-deadline, DEADLINE | WorkItem with hard SLA + notification to admin |
| WhatsApp from carer: "Mum had a good day today" | elder-care, STATUS_UPDATE | Update care case timeline + notification |
| Gmail invoice: "£450 for boiler repair" | contractor-coordination, INVOICE | Update contractor case, trigger payment approval gate |

### 6.4 Neocortex Role

| Capability | How it helps |
|-----------|-------------|
| Memory | "This phone number is the plumber we used last month" — actor recognition |
| CBR retrieval | "Last time we got a school email like this, we created a family task" — action pattern |
| CBR calibration | "Similar contractor jobs took 3 days and cost £200" — SLA/budget defaults |
| Trust scores | "This contractor has 0.4 deadline-reliability — add watchdog immediately" |
| Feature extraction | Parse amounts, dates, names from unstructured messages |

## 7. External System Sync Layer

> **Scope note:** This section is a product-context summary. External sync is a
> Phase 3 deliverable requiring its own detailed design spec before implementation
> — covering OAuth flows, conflict resolution, rate limiting, error handling, and
> idempotency. Included here to show the integration model for the UI layer.

Bidirectional sync between life's domain model and the user's existing ecosystem.

### 7.1 Sync Architecture

```
        Life Domain Model              External System
        ─────────────────              ───────────────
        ExternalActor          ←→      Google Contacts
        WorkItem (deadlines)   ──→     Google Calendar
        WorkItem (tasks)       ←→      Google Tasks
        Inbound messages       ←──     Gmail API
        Inbound messages       ←──     WhatsApp Business API
        Case milestones        ──→     Google Calendar
        Notifications          ──→     Push notifications (web)
```

### 7.2 Conflict Resolution

Life is the source of truth for accountability fields (trust, SLA, commitments,
audit). External systems are the source of truth for user-facing fields (name,
phone, email, calendar times). Conflicts:

| Scenario | Resolution |
|----------|-----------|
| Name changed in Google Contacts | Google wins — update ExternalActor |
| Trust score updated in life | Life wins — not synced to Google |
| Task completed in Google Tasks | Google wins — complete WorkItem |
| SLA deadline set in life | Life wins — create/update Google Calendar event |
| Calendar event moved by user | Google wins — update WorkItem deadline |
| Calendar event created by life | Life owns — subsequent user moves flow back |

### 7.3 Demo vs Production

| Concern | Demo mode | Production mode |
|---------|-----------|----------------|
| Contacts | Pre-populated ExternalActors | Google Contacts sync |
| Calendar | Mock calendar data | Google Calendar API |
| Tasks | WorkItems only | WorkItems + Google Tasks sync |
| Email intake | Simulated inbound messages | Gmail API push |
| WhatsApp intake | Simulated messages | WhatsApp Business API |
| Auth | Local OIDC with demo users | Real OIDC provider |

#### Demo Mode Architecture

**Activation:** Quarkus profile `demo` (`quarkus.profile=demo`). CDI `@IfBuildProfile("demo")`
selects demo data providers; production profile selects real sync providers. Same domain
model, same UI — only the data source layer differs.

**Data loading:** Flyway seed migrations at `db/life/demo/` (V9000+ range to avoid
collisions with domain migrations V100–V107 and ledger V2100+). Seeds run only when
`demo` profile is active via Quarkus `flyway.locations` profile configuration.

**Demo data scope:**
- 5 ExternalActors (plumber, GP, solicitor, school, carer) with realistic trust scores
- 3 active cases (contractor-coordination, care-coordination, travel-plan) at various stages
- 10–15 WorkItems across domains with SLA deadlines (some breached, some due soon)
- Commitment records showing COMMAND/RESPONSE lifecycle
- Ledger entries for audit trail demonstration
- 3 demo users (admin, member, junior) via Quarkus OIDC dev services

**No engine dependency:** Demo cases are static LifeCaseTracker records with pre-populated
timelines — they do not require the full engine stack running. This keeps demo mode
lightweight and fast-starting.

## 8. Conversational UI (Claudony Integration)

> **Scope note:** This section describes the target conversational UI design
> contingent on the Claudony embedding API investigation (§12.3). If Claudony
> cannot be embedded (e.g., requires its own Quarkus instance), the interaction
> model below remains valid but the implementation mechanism changes to direct
> Claude API integration within life-ui, with the same context-loading and
> API bridge patterns. Phase 2 deliverable.

### 8.1 Interaction Model

The conversational pane is a slide-out panel, context-aware — when opened from
a pending action, it receives the full decision context. The preferred
implementation is Claudony embedding; the fallback is a direct Claude API
integration within life-ui that implements the same context-loading and
API bridge patterns described below.

| Trigger | Context loaded | Example interaction |
|---------|---------------|-------------------|
| User clicks "Respond" on oversight gate | Gate details, financial amount, requester, CBR similar decisions | "Approve this £300 purchase?" → user responds naturally |
| User clicks "Respond" on family vote | Vote question, current tally, deadline | "Do you agree to the holiday dates?" |
| User clicks "Respond" on delegation | Task details, delegator, deadline | "Can you handle the grocery run this week?" |
| User opens chat unprompted | Current user context, recent activity | "Order groceries for this week" / "Find me a plumber" |
| Watchdog alert | Contractor details, commitment, elapsed time | "The plumber hasn't confirmed. Chase them or find someone else?" |

### 8.2 Claudony → Life API Bridge

Claudony translates natural language into life API calls:

| User says | API call |
|-----------|---------|
| "Approve" / "Yes, go ahead" | `POST /life-oversight-gates` (RESPONSE) |
| "Order groceries" | `POST /life-tasks` (HOUSEHOLD domain) |
| "Find me a plumber for Thursday" | `POST /life-cases` (CONTRACTOR_COORDINATION) |
| "How's the boiler repair going?" | `GET /life-cases/{id}` (case detail) |
| "What's on my plate today?" | `GET /pending-actions?candidateGroup={role}` |
| "Show me the plumber's track record" | `GET /external-actors/{id}` + trust data |

### 8.3 xterm.js Alternative

For power users or operator mode, xterm.js provides a terminal-style interface
within the same pane slot. The app shell supports swapping between Claudony
(conversational) and xterm.js (terminal) based on user preference.

## 9. New Backend Endpoints Required

### 9.1 REST Endpoints

| Method | Path | Purpose | Priority |
|--------|------|---------|----------|
| `GET` | `/life-cases` | List cases with filters (domain, status, type, date range) | Phase 1 |
| `GET` | `/life-cases/{id}` | Case detail with timeline, workers, channels | Phase 1 |
| `GET` | `/analytics/financial` | Spend tracking, budget compliance | Phase 2 |
| `GET` | `/life-cases/{id}/timeline` | Case lifecycle events | Phase 2 |
| `GET` | `/life-cases/{id}/workers` | Worker/agent execution history | Phase 2 |

#### Schema Changes Required

**LifeCaseTracker enrichment (V108 migration):**
- Add `domain` column (`LifeDomain` enum, derived from `caseType` at creation time)
- Each `LifeCaseType` maps to exactly one `LifeDomain` — set at `LifeCaseService.startCase()`

**LifeCaseResponse enrichment:**

| Response | Current fields | Additional fields needed |
|----------|---------------|------------------------|
| List (`GET /life-cases`) | caseId, caseType, status | + domain, createdAt, completedAt, slaState (derived — see below) |
| Detail (`GET /life-cases/{id}`) | (new) | caseId, caseType, domain, status, createdAt, completedAt, timeline events, active workers (from engine CaseInstance), channels (from qhorus), commitments, external actors involved |

**Case-level SLA state derivation:**
Computed at query time from WorkItems belonging to the case — not a stored column.

| Case SLA State | Condition |
|---------------|-----------|
| `BREACHED` | Any WorkItem within the case has a breached SLA |
| `AT_RISK` | No breaches, but any WorkItem is within SLA warning threshold (configurable, default: 4 hours before deadline) |
| `ON_TRACK` | All WorkItems are within SLA with no warnings |
| `COMPLETED` | Case is completed (no active SLA tracking) |

Derivation query: join `LifeCaseTracker.engineCaseId` → engine case → WorkItems
by scope prefix `casehubio/life/{domain}`, aggregate SLA states via worst-case.

**Case visibility policy:**
`LifeCaseVisibilityPolicy` SPI (matching the existing `LifeTaskVisibilityPolicy` pattern).
Junior sees cases where they have an assigned or candidate WorkItem within the case.
`LifeCaseResource` updated to include `JUNIOR` in `@RolesAllowed` once the visibility
policy filters results.

### 9.2 SSE Architecture & Endpoints

**Backend pattern:** Quarkus RESTEasy Reactive SSE. Each endpoint returns
`Multi<OutboundSseEvent>` from SmallRye Mutiny, backed by a `BroadcastProcessor`
that bridges CDI events to SSE streams.

```
CDI Event Bus                    SSE Resource                    Frontend
──────────────                   ────────────                    ────────
WorkItemLifecycleEvent ──┐
CaseLifecycleEvent ──────┼──→ LifeEventSseResource ──→ Multi<OutboundSseEvent>
SlaBreachEvent ──────────┘     (filters per principal      ──→ EventStreamController
                                via CurrentPrincipal)           (blocks-ui core)
```

**Key design points:**

| Concern | Approach |
|---------|----------|
| Event bus | CDI `@ObservesAsync` bridges existing lifecycle events to SSE emitters |
| Per-principal filtering | `CurrentPrincipal` on SSE connection; `LifeTaskVisibilityPolicy` filters events |
| Serialisation | JSON with `event:` type discriminator (`work-item-created`, `sla-breach`, etc.) |
| Reconnection | Snapshot-on-reconnect: server sends full current state on new connection. No event replay — `onOverflow(DROP)` makes replay unreliable. Client detects disconnect via heartbeat timeout, reconnects, receives fresh snapshot. |
| Heartbeats | `Multi.withInterval()` sends `:keepalive` comment every 30s |
| Backpressure | `BroadcastProcessor` with `onOverflow(DROP)` — SSE is best-effort; client polls on reconnect |

**Endpoints:**

| Path | Events | Purpose |
|------|--------|---------|
| `/events/inbox` | work-item-created, work-item-updated, sla-breach | Real-time inbox updates |
| `/events/notifications` | notification-created | Notification bell updates |
| `/events/cases` | case-started, case-completed, case-faulted | Dashboard case status |

### 9.3 Sync Endpoints (Production Mode)

| Path | Purpose |
|------|---------|
| `/sync/google-contacts` | Trigger/status for Google Contacts sync |
| `/sync/google-calendar` | Trigger/status for Google Calendar sync |
| `/sync/google-tasks` | Trigger/status for Google Tasks sync |
| Webhook receivers | Gmail push, WhatsApp Business API callbacks |

## 10. New blocks-ui Components

### 10.1 Components to Build

| Component | Tag | Purpose | Consumer |
|-----------|-----|---------|----------|
| Calendar Strip | `<calendar-strip>` | Compact upcoming-events strip for dashboard | Life, potentially others |
| Agent Activity Panel | `<agent-activity-panel>` | Sentinel/worker execution feed with decisions | Life, DevTown |
| Commitment Lifecycle | `<commitment-lifecycle>` | Commitment state progression | Life, Clinical, DevTown (blocks-ui #55) |

### 10.2 Existing Components Used

| Component | View(s) |
|-----------|---------|
| `<work-item-workbench>` | Inbox (primary composition) |
| `<work-item-inbox>` | Inbox, Dashboard (compact) |
| `<work-item-detail>` | Inbox detail pane |
| `<split-workbench>` | Inbox layout, Cases layout |
| `<list-pane>` | People list, task lists |
| `<detail-pane>` | People detail, Case detail |
| `<kpi-metric-row>` | Dashboard, Journal |
| `<sla-indicator>` | Dashboard, Inbox detail |
| `<sla-breach-policy>` | Dashboard, Inbox detail |
| `<approval-gate>` | Inbox detail (oversight/risk decisions) |
| `<trust-score-panel>` | People detail, Inbox detail, Journal |
| `<trust-feedback-display>` | Inbox detail (post-decision) |
| `<similarity-panel>` | Inbox detail, Cases detail (CBR) |
| `<routing-rationale>` | Inbox detail, Cases detail |
| `<audit-trail-viewer>` | Cases detail, Journal |
| `<blocks-timeline>` | People activity, Cases timeline |
| `<compliance-summary>` | Journal |
| `<grouped-data-view>` | Dashboard, Cases list, Journal |
| `<notification-bell>` | App shell header (subcomponent of `notification-inbox` package) |
| `<notification-inbox>` | Notification dropdown |
| `<channel-activity>` | Inbox detail, Cases detail |
| `<work-item-row>` | Inbox list items |
| `<gdpr-erasure-action>` | People detail |

## 11. Implementation Phases

Delivered as Arc42Stories chapters. Each phase is independently deployable
and valuable.

### Phase 1 — Skeleton + Inbox (MVP)

**Goal:** A working standalone app where household members can see and act on
their tasks.

**Delivers:**
- App shell with routing, nav, auth, theme
- Inbox view (full `<work-item-workbench>` composition)
- Dashboard view (KPI strip + pending actions compact + SLA warnings)
- SSE event infrastructure (CDI→SSE bridge, `/events/inbox` endpoint)
- `GET /life-cases` and `GET /life-cases/{id}` backend endpoints (with enriched responses)
- `LifeCaseTracker` schema migration (V108 — domain column)
- `LifeCaseVisibilityPolicy` SPI for role-based case filtering
- Pre-populated demo data (Flyway seeds, Quarkus `demo` profile)
- blocks-ui #56 updated with life as a consuming app

**Does not deliver:** Conversational UI (Claudony — Phase 2, pending embedding
API investigation), external system sync, ambient intake, People view,
Cases view, Journal view, Google integration.

### Phase 2 — Visibility + Conversational UI

**Goal:** Full operational visibility across cases, people, and decisions.
Conversational UI for human-in-the-loop interaction.

**Delivers:**
- Cases view (grouped list + tabbed detail with timeline, workers, audit, CBR)
- People & Contacts view (list + tabbed detail with trust, activity, GDPR)
- Journal & Reports view (decision log, SLA compliance, trust trends)
- Claudony pane (embedded, context-aware — pending Claudony embedding API design)
- Additional analytics endpoints
- IoT panel embedding (house status on dashboard)
- `<agent-activity-panel>` and `<calendar-strip>` components in blocks-ui

### Phase 3 — External Integration

**Goal:** Meet people where they are — sync with their existing tools.

**Delivers:**
- Google Contacts ↔ ExternalActor bidirectional sync
- Google Calendar ↔ WorkItem deadline sync
- Google Tasks ↔ WorkItem projected view sync
- Calendar preview on dashboard (real data)
- Sync status/configuration UI

### Phase 4 — Ambient Intake

**Goal:** The system listens to your life and creates structure automatically.

**Delivers:**
- Gmail API push → intake classification → case/task creation
- WhatsApp Business API webhook → intake classification → case/task creation
- Neocortex CBR + memory for intake routing and classification
- Notification pipeline: intake events → SSE → inbox
- Intake activity feed (what the system noticed and did)

### Phase 5 — Life Management (Vision)

> **Note:** This phase is a product direction signal, not a scoped deliverable.
> Each capability listed below requires its own design work — including defining
> the suggestion engine, pattern detection data model, and cross-domain intelligence
> architecture. Detailed specs will be written before implementation begins.

**Goal:** Proactive life management — the system doesn't just react, it manages.

**Capabilities (require design specs):**
- Proactive suggestions ("boiler service is due — last done 11 months ago") — likely neocortex CBR retrieval with temporal pattern matching
- Pattern detection ("grocery spend up 20% this month") — requires financial transaction model (not yet designed)
- Automated recurring task creation from patterns — extends CBR adaptation rules
- Cross-domain intelligence ("school holiday next week → adjust care schedule") — cross-case signal extension
- Full Claudony conversational capabilities for ad-hoc life management

## 12. Cross-Module Dependencies

### 12.1 blocks-ui

| Need | blocks-ui issue | Status |
|------|----------------|--------|
| Life added to app delivery epic | #56 | To file |
| `<commitment-lifecycle>` | #55 | Open |
| `<calendar-strip>` | — | To file |
| `<agent-activity-panel>` | — | To file |

> **Pre-approval action:** All "To file" entries must have blocks-ui issues
> created before this spec is approved, so the dependency chain is visible.
>
> **Note:** `<routing-rationale>`, `<channel-activity>`, and `<compliance-summary>`
> already exist in blocks-ui and are listed in §10.2 — they are not new dependencies.

### 12.2 casehub-life backend

| Need | Life issue | Status |
|------|-----------|--------|
| `GET /life-cases` list endpoint | — | To file |
| `GET /life-cases/{id}` detail endpoint | — | To file |
| SSE event endpoints | — | To file |
| Google sync layer | — | To file (Phase 3) |
| Ambient intake pipeline | — | To file (Phase 4) |

### 12.3 Upstream

| Need | Repo | Issue | Status |
|------|------|-------|--------|
| OpenClaw skill integration | life | #60 | Open, blocked on openclaw Epic 4 |
| Claudony embedding API | claudony | — | To investigate |
| IoT webapp embed/link API | iot | — | To investigate |

## 13. Data Flow Summary

```
External World                    Life Backend                    Life UI
──────────────                    ────────────                    ───────
WhatsApp msg ──────→ Intake ──→ WorkItem/Case ──→ SSE ──→ Inbox notification
Gmail email ───────→ Intake ──→ WorkItem/Case ──→ SSE ──→ Inbox item
Google Calendar ───→ Sync ───→ WorkItem deadline
Google Contacts ───→ Sync ───→ ExternalActor
                                                          User clicks "Approve"
                                                     ──→ Claudony
                                                     ──→ POST /oversight-gate
                               WorkItem updated ←────────
                               Ledger entry ←────────────
                                                  SSE ──→ Dashboard KPI update
Google Tasks ←─────── Sync ←── WorkItem created
Google Calendar ←──── Sync ←── Case milestone
```
