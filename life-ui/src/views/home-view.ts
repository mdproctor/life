import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { MetricDefinition } from '@casehubio/blocks-ui-kpi-metric-row';
import '@casehubio/blocks-ui-kpi-metric-row';
import '@casehubio/blocks-ui-grouped-data-view';

interface CaseTypeStats {
  caseType: string;
  total: number;
  active: number;
  completed: number;
  failed: number;
  completionRate: number | null;
}

interface DomainSlaStats {
  domain: string;
  totalWithSla: number;
  breachedCount: number;
  complianceRate: number | null;
}

@customElement('home-view')
export class HomeView extends LitElement {
  @state() private caseMetrics: MetricDefinition[] = [];
  @state() private slaMetrics: MetricDefinition[] = [];

  static override styles = css`
    :host { display: block; }

    h1 {
      margin: 0 0 var(--pages-space-5, 20px);
      font-size: var(--pages-font-size-2xl, 24px);
      font-weight: 600;
      color: var(--pages-neutral-12, #111);
    }

    h2 {
      margin: 0 0 var(--pages-space-3, 12px);
      font-size: var(--pages-font-size-lg, 16px);
      font-weight: 500;
      color: var(--pages-neutral-11, #262626);
    }

    section {
      margin-bottom: var(--pages-space-6, 24px);
    }

    .card {
      background: var(--pages-neutral-1, #fff);
      border-radius: var(--pages-radius-lg, 8px);
      padding: var(--pages-space-4, 16px);
      margin-bottom: var(--pages-space-4, 16px);
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    this._loadMetrics();
  }

  private async _loadMetrics(): Promise<void> {
    try {
      const [casesResp, slaResp] = await Promise.all([
        fetch('/analytics/cases'),
        fetch('/analytics/sla'),
      ]);

      if (casesResp.ok) {
        const data = await casesResp.json();
        this.caseMetrics = this._toCaseMetrics(data.entries ?? []);
      }

      if (slaResp.ok) {
        const data = await slaResp.json();
        this.slaMetrics = this._toSlaMetrics(data.entries ?? []);
      }
    } catch {
      // Endpoints may not be available in demo mode — show empty state
    }
  }

  private _toCaseMetrics(entries: CaseTypeStats[]): MetricDefinition[] {
    const totalActive = entries.reduce((sum, e) => sum + e.active, 0);
    const totalCompleted = entries.reduce((sum, e) => sum + e.completed, 0);
    const totalFailed = entries.reduce((sum, e) => sum + e.failed, 0);
    const total = entries.reduce((sum, e) => sum + e.total, 0);

    return [
      { key: 'active', value: totalActive, label: 'Active Cases', status: 'normal' as const },
      { key: 'completed', value: totalCompleted, label: 'Completed', status: 'normal' as const },
      { key: 'failed', value: totalFailed, label: 'Failed', status: totalFailed > 0 ? 'warning' as const : 'normal' as const },
      { key: 'total', value: total, label: 'Total Cases' },
    ];
  }

  private _toSlaMetrics(entries: DomainSlaStats[]): MetricDefinition[] {
    const totalBreached = entries.reduce((sum, e) => sum + e.breachedCount, 0);
    const totalSla = entries.reduce((sum, e) => sum + e.totalWithSla, 0);
    const avgCompliance = totalSla > 0
      ? Math.round((1 - totalBreached / totalSla) * 100)
      : 100;

    return [
      { key: 'sla-compliance', value: `${avgCompliance}%`, label: 'SLA Compliance', status: avgCompliance >= 90 ? 'normal' as const : 'warning' as const },
      { key: 'sla-breached', value: totalBreached, label: 'SLA Breaches', status: totalBreached > 0 ? 'critical' as const : 'normal' as const },
      { key: 'sla-tracked', value: totalSla, label: 'SLA-Tracked Items' },
    ];
  }

  override render() {
    return html`
      <h1>Dashboard</h1>

      <section>
        <div class="card">
          <h2>Cases</h2>
          <kpi-metric-row .metrics=${this.caseMetrics} density="compact"></kpi-metric-row>
        </div>
      </section>

      <section>
        <div class="card">
          <h2>SLA Compliance</h2>
          <kpi-metric-row .metrics=${this.slaMetrics} density="compact"></kpi-metric-row>
        </div>
      </section>

      <section>
        <div class="card">
          <h2>Active Cases by Domain</h2>
          <grouped-data-view
            endpoint="/life-cases?status=ACTIVE"
            group-by="domain"
            data-path="items"
          ></grouped-data-view>
        </div>
      </section>
    `;
  }
}
