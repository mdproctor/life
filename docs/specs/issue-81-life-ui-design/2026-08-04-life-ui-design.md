# Life UI — Household Hub Design

**Date:** 2026-08-04
**Issue:** #81
**Status:** Reviewed (light)
**Supersedes:** `docs/specs/2026-07-19-household-hub-ui-design.md` (retains vision, replaces implementation plan)
**Pages issue:** casehub-pages#285 (dock-workbench component)

## 1. Product Concept

A desktop-first personal life management interface that surfaces CaseHub's
8 foundation layers as a family coordination hub. Two tiers:

**Tier 1 (build now):** Coordination UI — real platform data. Tasks, cases,
contacts, trust, commitments, SLA compliance, CBR insights. Composed from
blocks-ui Web Components, wired to existing REST + SSE endpoints.

**Tier 2 (mock now, build later):** Executive assistant — ambient intake
(email, WhatsApp), financial tracking (Open Banking), layered family
summaries, proactive management. Static HTML mockups in dock panels
showing the vision; backend integration deferred.

## 2. Demo Household

A realistic household that exercises all domains and roles:

| Actor | Role | Exercises |
|-------|------|-----------|
| Mark (dad) | `household-admin` | Full authority, financial approvals, contractor management |
| Sarah (mum) | `household-member` | All domains, task delegation, health coordination |
| Ella (15) | `household-junior` | Own tasks only, school events, activities |
| Tom (11) | `household-junior` | Own tasks only, school events, activities |
| Grandma Jean | External actor (elder-care) | Care-coordination cases, health monitoring |
| Bob's Plumbing | External actor (contractor) | Trust scores, commitment tracking, watchdog |
| Dr. Patel | External actor (health) | Appointment cycles, follow-up tracking |
| Harris & Co Solicitors | External actor (legal) | Legal deadlines, contract renewals |

Flyway demo seeds (V9000+ range, `demo` profile only) populate a full week
of realistic family life across all 8 domains.

## 3. Layout — Dock Workbench

IntelliJ-style dock layout with collapsible, resizable panels on left and
right, centre content fills remaining space.

```
┌─────────────────────────────────────────────────────────────────┐
│  [Household Hub]   Home  Inbox  People  Cases  Journal    🔔 👤 │
├────────┬──────────────────────────────────────┬─────────────────┤
│ LEFT   │          CENTRE                      │ RIGHT           │
│ DOCKS  │                                      │ DOCKS           │
│        │  ┌─ Morning Briefing ─────────────┐  │                 │
│ ☐ Inbox│  │ 3 items need attention today    │  │ ☐ Family       │
│        │  │ Plumber confirmed for 2pm       │  │   Summary      │
│ ☐ Cases│  │ Ella's swim cancelled           │  │                │
│        │  └────────────────────────────────-┘  │ ☐ Money        │
│ ☐ Cal  │                                      │   (mock)        │
│        │  ┌─ KPI Strip ───────────────────-┐  │                 │
│        │  │ Active:5 │ SLA:94% │ Due:3     │  │ ☐ Comms        │
│        │  └────────────────────────────────-┘  │   (mock)        │
│        │                                      │                 │
│        │  ┌─ Action Items ─────────────────┐  │                 │
│        │  │ ⚠ Approve £450 boiler invoice   │  │                 │
│        │  │ ⚠ Respond to school trip consent│  │                 │
│        │  │ ○ GP follow-up overdue (3 days) │  │                 │
│        │  └────────────────────────────────-┘  │                 │
│        │                                      │                 │
│        │  ┌─ Active Cases by Domain ───────┐  │                 │
│        │  │ grouped-data-view              │  │                 │
│        │  └────────────────────────────────-┘  │                 │
└────────┴──────────────────────────────────────┴─────────────────┘
```

### 3.1 Dock Component (`<dock-workbench>`)

**Status:** Requested from pages team. Requirements:

- Left and right dock bars with vertical labels
- Click label to toggle panel open/close
- Drag to resize open panels
- Centre content fills remaining space (flexbox `flex: 1`)
- Layout state persisted to localStorage (which docks open, widths)
- Named slots for centre and each dock panel
- Bottom dock (optional — for conversational pane or notifications)
- Multiple panels per side (tabbed or vertically stacked)
- Configurable min/max widths with auto-collapse at breakpoint
- Keyboard shortcuts (Alt+1, Alt+2 etc) to toggle docks

**Fallback:** If the dock component isn't ready, prototype with CSS grid
and `<details>` elements in life-ui. Extract to blocks-ui once API settles.

### 3.2 Navigation

Top navigation bar retained from existing app-shell:
Home | Inbox | People | Cases | Journal

The dashboard (Home) is the dock workbench. Other views use their own
layouts (Inbox uses `<split-workbench>`, People/Cases use list+detail).

## 4. Centre Content — Summary Stack

Vertical composition, most urgent at top:

### 4.1 Morning Briefing

Summary of overnight activity and today's priorities. Initially a
template-driven card (no LLM), populated from queries:

```
Good morning, Mark. 3 items need your attention today.
• Bob's Plumbing confirmed for 2pm (contractor-coordination)
• Ella's swimming cancelled — direct debit still active
• GP follow-up for Jean is 3 days overdue
```

**Data aggregation:** A single `GET /dashboard/briefing` endpoint returns
the pre-assembled briefing. Server-side aggregation queries:
- `GET /pending-actions?candidateGroup={role}&dueBefore={endOfDay}` — today's actions
- Recent `WorkItemLifecycleEvent` and `CaseLifecycleEvent` (last 12h) from event log
- Active SLA breaches from `LifeSlaBreachPolicy` state

The endpoint returns a structured `BriefingResponse`:
```json
{
  "greeting": "Good morning, Mark",
  "actionCount": 3,
  "items": [
    {"text": "Bob's Plumbing confirmed for 2pm", "domain": "CONTRACTOR_COORDINATION", "type": "case-update"},
    {"text": "GP follow-up for Jean is 3 days overdue", "domain": "ELDER_CARE", "type": "sla-breach"}
  ]
}
```

The `<morning-briefing>` component (life-ui local, not blocks-ui) renders
this response as a card. No client-side aggregation.

**Future (Tier 2):** LLM-generated briefing from email/WhatsApp overnight
activity, synthesised with platform state.

### 4.2 KPI Strip

`<kpi-metric-row>` — already implemented in `home-view.ts`.

| Metric | Source | Status logic |
|--------|--------|-------------|
| Active Cases | `/analytics/cases` | normal |
| SLA Compliance | `/analytics/sla` | warning < 90% |
| Pending Actions | `/pending-actions` count | warning > 5 |
| Due Today | `/pending-actions` filtered | critical if overdue |
| Trust Average | `/analytics/trust` | warning < 0.6 |

### 4.3 Action Items

Items requiring the current user's decision or action. Sorted by urgency.

**Data source:** `GET /pending-actions?candidateGroup={role}` returns a
unified list. The existing `PendingActionsResource` already aggregates
WorkItems and qhorus commitments into a single response with `actionType`
discriminator (WORK_ITEM, OVERSIGHT_GATE, DELEGATION, WATCHDOG_ALERT).

| Type | Component | `actionType` |
|------|-----------|-------------|
| Oversight gates | `<approval-gate>` | OVERSIGHT_GATE |
| Overdue tasks | `<work-item-row>` | WORK_ITEM (SLA breached) |
| Due-soon tasks | `<work-item-row>` | WORK_ITEM (within 4h) |
| Watchdog alerts | `<sla-indicator>` | WATCHDOG_ALERT |
| Delegation requests | `<work-item-row>` | DELEGATION |

Click any item → navigates to Inbox detail view with full context.

### 4.4 Active Cases by Domain

`<grouped-data-view>` — already implemented in `home-view.ts`.
Groups by `LifeDomain`, shows case type, status, SLA state.

## 5. Left Dock Panels (Real Data)

### 5.1 Inbox Summary

Compact version of the Inbox view. Shows count + top 3 items per tab
(My Work / Claimable / All). Uses `<work-item-inbox>` in compact mode.

Click any item → navigates to `#inbox` with that item selected.

### 5.2 Cases Overview

Active cases grouped by domain with status indicators. Uses
`<grouped-data-view>` with compact density.

Click any case → navigates to `#cases` with that case selected.

### 5.3 Calendar Preview (Mock)

Static HTML showing a realistic week with colour-coded events by domain
and family member. Demonstrates the vision of unified calendar view.

**Future (Tier 2):** Populated from WorkItem deadlines, case milestones,
and Google Calendar integration. Requires a `GET /dashboard/calendar`
endpoint aggregating deadline data — not yet built.

## 6. Right Dock Panels

### 6.1 Family Summary (Real + Mock)

Per-person summary cards for each household member.

**Real data:** Each person's pending tasks count, active cases, recent
activity (from WorkItems where `assigneeId` or `candidateGroups` match).

**Mock overlay:** Static HTML showing what the layered summary would
look like with external data:

```
┌─ Ella (15) ────────────────────────┐
│ School: Parent evening Tue 7pm     │  ← mock (email extraction)
│ Swimming: Cancelled this week      │  ← mock (WhatsApp extraction)
│ Tasks: 2 pending (real)            │  ← real (platform data)
│ Health: Dentist overdue            │  ← real (WorkItem SLA)
└────────────────────────────────────┘
```

### 6.2 Money Panel (Mock)

Static HTML showing the financial tracking vision:

```
┌─ This Month ───────────────────────┐
│ Total spend: £3,240                │
│ ├─ Household: £1,890               │
│ ├─ Ella: £420 (school, swimming)   │
│ ├─ Tom: £380 (school, football)    │
│ └─ Other: £550                     │
│                                    │
│ Bills due:                         │
│ ○ Council tax £180 (5th)           │
│ ○ Electricity £95 (12th)           │
│ ○ Broadband £45 (15th)            │
│                                    │
│ ⚠ Netflix increased £2/mo          │
└────────────────────────────────────┘
```

Label: "Coming soon — Open Banking integration"

### 6.3 Comms Panel (Mock)

Static HTML showing email/WhatsApp ambient intake vision:

```
┌─ Recent Activity ──────────────────┐
│ 📧 School: Trip consent form       │
│    → extracted: deadline 15 Aug    │
│    → created: task for Mark        │
│                                    │
│ 💬 Bob's Plumbing: "Thursday 2pm"  │
│    → extracted: appointment        │
│    → updated: contractor case      │
│    → trust: confirmed commitment   │
│                                    │
│ 📧 Solicitor: "Respond by Aug 30" │
│    → extracted: legal deadline     │
│    → created: SLA-tracked task     │
└────────────────────────────────────┘
```

Label: "Coming soon — Email & WhatsApp integration"

## 7. Other Views

The 5-tab navigation remains. This spec owns the Home view. Other views
are summarised here; each will get its own detailed spec when implementation
begins.

| View | Layout | Key Components | Status |
|------|--------|---------------|--------|
| **Home** | Dock workbench (this spec) | Morning briefing, KPI strip, action items, dock panels | New design |
| **Inbox** | `<split-workbench>` (list + detail) | `<work-item-inbox>` (3-tab: My Work / Claimable / All), `<work-item-detail>` with action bar, approval gates, SLA indicator, trust panel | Scaffolded — `inbox-view.ts` wires `<work-item-workbench>` to `/pending-actions` |
| **People** | `<split-workbench>` (list + tabbed detail) | `<list-pane>` with search/filter → `/external-actors`. Detail tabs: Trust (`<trust-score-panel>`), Activity (`<blocks-timeline>`), Tasks, GDPR (`<gdpr-erasure-action>`) | Not started |
| **Cases** | `<split-workbench>` (grouped list + tabbed detail) | `<grouped-data-view>` by domain → `GET /life-cases`. Detail tabs: Timeline, Workers (`<agent-activity-panel>` — new), Routing (`<routing-rationale>`), Audit (`<audit-trail-viewer>`), CBR (`<similarity-panel>`), Commitments, Channels | Not started |
| **Journal** | Full-width stacked sections | Decision log (`<audit-trail-viewer>`), SLA compliance (`<kpi-metric-row>` + `<compliance-summary>`), trust trends (`<trust-score-panel>` trend), domain breakdown (`<grouped-data-view>`) | Not started |

## 8. SSE Event Architecture

Reuse the existing `LifeEventSseResource` + `LifeEventBroadcaster` +
`LifeEventBridge` (already built in Layer 7). The SSE infrastructure
bridges CDI events to browser-side `EventStreamController`.

**Single multiplexed stream:** Dashboard connects to one SSE endpoint
(`/events/stream`) to avoid browser connection limits (6 per origin in
HTTP/1.1). Events carry a `type` field for client-side routing:

| Event type | Payload | Consumers |
|-----------|---------|-----------|
| `work-item-created` | WorkItem summary | Inbox dock, Action Items, KPI strip |
| `work-item-updated` | WorkItem summary | Inbox dock, Action Items, KPI strip |
| `sla-breach` | Breach details | Action Items, KPI strip |
| `case-started` | Case summary | Cases dock, Active Cases section |
| `case-completed` | Case summary | Cases dock, Active Cases section, KPI strip |
| `case-faulted` | Case summary | Cases dock |

**Per-principal filtering:** `LifeEventSseResource` reads `CurrentPrincipal`
from the SSE connection's security context. Events are filtered through
`LifeTaskVisibilityPolicy` before emission — junior users only receive
events for their own tasks. The existing `LifeEventBroadcaster` already
receives CDI events; the filter is applied at the SSE resource layer.

**Connection lifecycle:** SSE connection opens on dashboard mount, closes
on `disconnectedCallback()`. Hidden dock panels remain in DOM (display:none)
and continue receiving events — toggling a dock doesn't reconnect SSE.

**Initial load:** Dashboard fetches REST data on mount, then subscribes to
SSE. Late-arriving SSE events for items already loaded are deduplicated by
ID on the client side (component maintains a `Set<string>` of known IDs).

## 9. Demo Mode

**Activation:** `quarkus.profile=demo` (existing pattern).

**Start command:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn quarkus:dev -pl app -Dquarkus.profile=demo
```

**Demo seeds (V9000+ range):**

| Seed | Content |
|------|---------|
| V9001 | Demo users (Mark/admin, Sarah/member, Ella/junior, Tom/junior) |
| V9002 | External actors (Bob's Plumbing, Dr. Patel, Harris & Co, Ella's School, Jean's Carer) with trust scores |
| V9003 | 5 active cases across domains (contractor, health, care, travel, financial) |
| V9004 | 15 WorkItems with SLA deadlines (some breached, some due today, some future) |
| V9005 | Commitment records (COMMAND/RESPONSE lifecycle, oversight gates) |
| V9006 | Ledger entries for audit trail |
| V9007 | LifeCaseTracker records with pre-populated timelines |

**No engine dependency in demo:** Cases are static LifeCaseTracker records.
The full engine stack is not required for demo mode.

## 10. Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Lit 3.x (Web Components) |
| Components | blocks-ui (`@casehubio/agency-ui-*`) via `.casehub-packages/` |
| Tokens | `@casehubio/pages-ui-tokens` (OKLCH scales, spacing, typography) |
| Build | Vite (via Quinoa) |
| Serving | Quarkus + Quinoa (`quarkus.profile=demo`) |
| Real-time | SSE via existing `LifeEventSseResource` |
| Auth | OIDC (already wired via life#40; dev services for demo) |
| Data fetching | `fetch()` to REST endpoints |

## 11. Implementation Scope

### Build (real data, real components)

- [ ] Dock workbench layout (pending pages team casehub-pages#285 — CSS grid fallback)
- [ ] `GET /dashboard/briefing` endpoint (server-side aggregation)
- [ ] `<morning-briefing>` component (life-ui local)
- [ ] KPI strip (enhance existing `home-view.ts`)
- [ ] Action items panel (wired to `/pending-actions` with `actionType` routing)
- [ ] Inbox dock (compact `<work-item-inbox>`)
- [ ] Cases dock (compact `<grouped-data-view>`)
- [ ] Family summary (per-person task counts + active cases from real data)
- [ ] SSE multiplexed stream (`/events/stream`) with per-principal filtering
- [ ] Demo Flyway seeds (V9001–V9007)
- [ ] App shell updates (notification bell, user identity, theme toggle)

### Mock (static HTML placeholders)

- [ ] Calendar preview — realistic week colour-coded by domain and family member
- [ ] Money panel — household spend breakdown, bills due, child-attributed costs
- [ ] Comms panel — email/WhatsApp extraction examples with intake pipeline visualisation
- [ ] Family summary enrichment — school events, activity schedules, WhatsApp extractions
- [ ] Layered summary drill-down — per-child, per-domain, per-week views
- [ ] Conversational pane — slide-out right panel placeholder

Each mock panel includes a "Coming soon" label and visually demonstrates
the feature's value without backend integration.

## 12. Future Tiers (Design Later)

Each requires its own design spec before implementation:

| Tier | Capability | Dependencies |
|------|-----------|-------------|
| Financial tracking | Open Banking (TrueLayer/Yapily), transaction categorisation, child-attributed spend | FCA-regulated, OAuth flows |
| Email intake | Gmail API push, LLM classification, task/deadline extraction | Neocortex integration |
| WhatsApp intake | WhatsApp Business API, message parsing, actor recognition | Connector module |
| Layered summaries | LLM-backed per-person/domain/time summaries from ingested events | Summary service, OpenClaw agents |
| Conversational UI | Natural language → API calls, context-aware decision support | Agent infrastructure |
| Google sync | Contacts ↔ ExternalActor, Calendar ↔ deadlines, Tasks ↔ WorkItems | OAuth, conflict resolution |
| Mobile | Responsive layout, push notifications | PWA or native |
