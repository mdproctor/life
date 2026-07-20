import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import type { WorkIdentity } from '@casehubio/blocks-ui-core';
import '@casehubio/blocks-ui-work-item-workbench';

const DEMO_IDENTITY: WorkIdentity = {
  userId: 'demo-admin',
  displayName: 'Demo Admin',
  groups: ['household-admin'],
  tenancyId: '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
};

@customElement('inbox-view')
export class InboxView extends LitElement {
  static override styles = css`
    :host {
      display: block;
      height: 100%;
    }

    work-item-workbench {
      height: 100%;
    }
  `;

  override render() {
    return html`
      <work-item-workbench
        .identity=${DEMO_IDENTITY}
        endpoint="/pending-actions"
      ></work-item-workbench>
    `;
  }
}
