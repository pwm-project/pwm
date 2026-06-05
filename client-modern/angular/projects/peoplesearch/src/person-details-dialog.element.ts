/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2026 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { LitElement, html, nothing, type TemplateResult } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { getPerson, getTeamEmails, exportOrgChartUrl } from './services/people-service';
import { personDetailsConfig, type PersonDetailsConfig } from './services/config-service';
import { navigate } from './nav';
import type { Person, PersonDetailAttribute } from './models';

/** Which sub-dialog (if any) is layered on top of the details view. */
type SubDialog = 'none' | 'export' | 'email';

/**
 * Native port of the legacy {@code PersonDetailsDialogComponent} +
 * {@code orgchart-export} / {@code orgchart-email} controllers (issue #729,
 * session 6).  A modal showing a person's photo, display names, attribute
 * table (with userDN / email / tel / searchable handling), and the Org Chart /
 * Export / Email Team actions.  Closing dispatches {@code dialog-close}; all
 * navigation (org chart, related people, searchable values) routes through the
 * host via {@code ps-navigate}.  Light-DOM render root for PWM theme cascade.
 */
@customElement('pwm-peoplesearch-details')
export class PersonDetailsDialogElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    @property({ type: String }) userKey = '';

    @state() private person: Person | null = null;
    @state() private config: PersonDetailsConfig | null = null;
    @state() private loading = true;
    @state() private errorMessage = '';

    @state() private sub: SubDialog = 'none';
    @state() private depth = 1;
    @state() private teamEmails = '';
    @state() private fetchingEmails = false;

    private fetchController: AbortController | null = null;
    private keydownListener: ((e: KeyboardEvent) => void) | null = null;

    override connectedCallback(): void {
        super.connectedCallback();
        this.keydownListener = (e: KeyboardEvent): void => {
            if (e.key === 'Escape') {
                this.close();
            }
        };
        window.addEventListener('keydown', this.keydownListener);
        void this.load();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        if (this.fetchController) {
            this.fetchController.abort();
            this.fetchController = null;
        }
        if (this.keydownListener) {
            window.removeEventListener('keydown', this.keydownListener);
            this.keydownListener = null;
        }
    }

    override updated(changed: Map<string, unknown>): void {
        if (changed.has('userKey')) {
            // changed.get returns the OLD value; undefined on first render
            // (connectedCallback already kicked off the initial load).  Reload
            // only when navigating between related people without unmounting.
            const prev = changed.get('userKey') as string | undefined;
            if (prev !== undefined && prev !== this.userKey) {
                void this.load();
            }
        }
    }

    private async load(): Promise<void> {
        if (this.fetchController) {
            this.fetchController.abort();
        }
        const controller = new AbortController();
        this.fetchController = controller;
        this.loading = true;
        this.errorMessage = '';
        this.sub = 'none';
        try {
            const [person, config] = await Promise.all([
                getPerson(this.userKey, controller.signal),
                personDetailsConfig(controller.signal),
            ]);
            if (controller.signal.aborted) {
                return;
            }
            this.person = person;
            this.config = config;
        } catch (err) {
            if ((err as { name?: string }).name === 'AbortError') {
                return;
            }
            this.errorMessage = (err as Error).message;
        } finally {
            if (this.fetchController === controller) {
                this.fetchController = null;
            }
            this.loading = false;
        }
    }

    private close = (): void => {
        this.dispatchEvent(new CustomEvent('dialog-close', { bubbles: true, composed: true }));
    };

    // ---- actions ----

    private gotoOrgChart = (): void => {
        if (this.person?.userKey) {
            navigate(this, { kind: 'orgchart', key: this.person.userKey });
        }
    };

    private gotoRelated(userKey: string): void {
        navigate(this, { kind: 'details', key: userKey });
    }

    private searchFor(value: string): void {
        navigate(this, { kind: 'search', query: value });
    }

    private beginExport = (): void => {
        this.depth = 1;
        this.sub = 'export';
    };

    private runExport = (): void => {
        if (this.person?.userKey) {
            window.location.href = exportOrgChartUrl(this.person.userKey, this.depth);
        }
        this.sub = 'none';
    };

    private beginEmail = (): void => {
        this.depth = 1;
        this.sub = 'email';
        void this.fetchEmails();
    };

    private async fetchEmails(): Promise<void> {
        if (!this.person?.userKey) {
            return;
        }
        this.fetchingEmails = true;
        try {
            const emails = await getTeamEmails(this.person.userKey, this.depth);
            this.teamEmails = emails.join(',');
        } catch (err) {
            this.errorMessage = (err as Error).message;
        } finally {
            this.fetchingEmails = false;
        }
    }

    private onDepthChange(value: string): void {
        this.depth = Number(value) || 1;
        if (this.sub === 'email') {
            void this.fetchEmails();
        }
    }

    private sendEmail = (): void => {
        if (this.teamEmails) {
            window.location.href = `mailto:${this.teamEmails}`;
        }
        this.sub = 'none';
    };

    // ---- render ----

    override render(): TemplateResult {
        return html`
            <div
                class="pwm-ps-dialog-backdrop"
                @click=${(e: MouseEvent) => {
                    if (e.target === e.currentTarget) {
                        this.close();
                    }
                }}
            >
                <div class="pwm-ps-dialog" role="dialog" aria-modal="true" aria-label="Person details">
                    ${this.sub === 'none' ? this.renderDetails() : nothing}
                    ${this.sub === 'export' ? this.renderExport() : nothing}
                    ${this.sub === 'email' ? this.renderEmail() : nothing}
                </div>
            </div>
        `;
    }

    private renderDetails(): TemplateResult {
        if (this.loading) {
            return html`<div class="pwm-ps-dialog-body">Loading…</div>`;
        }
        if (this.errorMessage) {
            return html`
                <div class="pwm-ps-dialog-body pwm-ps-error">${this.errorMessage}</div>
                <div class="pwm-ps-dialog-actions">
                    <button type="button" class="pwm-ps-button" @click=${this.close}>Close</button>
                </div>
            `;
        }
        const person = this.person;
        if (!person) {
            return html``;
        }
        const cfg = this.config;
        const names = person.displayNames ?? [];
        const avatarStyle = cfg?.photosEnabled && person.photoURL
            ? `background-image:url(${cssUrl(person.photoURL)})`
            : '';
        return html`
            <div class="pwm-ps-dialog-header">
                ${cfg?.photosEnabled
                    ? html`<div class="pwm-ps-avatar" style=${avatarStyle} aria-hidden="true"></div>`
                    : nothing}
                <div class="pwm-ps-header-main">
                    <div class="pwm-ps-header-top">
                        <h2 title=${names[0] ?? ''}>${names[0] ?? ''}</h2>
                        <span class="pwm-ps-fill"></span>
                        <button type="button" class="pwm-ps-icon-button" title="Close" aria-label="Close" @click=${this.close}>&times;</button>
                    </div>
                    ${names[1] ? html`<div class="pwm-ps-header-line">${names[1]}</div>` : nothing}
                    ${names[2] ? html`<div class="pwm-ps-header-line">${names[2]}</div>` : nothing}
                    ${names[3] ? html`<div class="pwm-ps-header-line">${names[3]}</div>` : nothing}
                    <div class="pwm-ps-dialog-action-row">
                        ${cfg?.orgChartEnabled
                            ? html`<button type="button" class="pwm-ps-button" @click=${this.gotoOrgChart}>Organizational Chart</button>`
                            : nothing}
                        ${cfg?.exportEnabled
                            ? html`<button type="button" class="pwm-ps-button" title="Export Org Chart" @click=${this.beginExport}>Export</button>`
                            : nothing}
                        ${cfg?.emailTeamEnabled
                            ? html`<button type="button" class="pwm-ps-button" title="Email Team" @click=${this.beginEmail}>Email Team</button>`
                            : nothing}
                    </div>
                </div>
            </div>
            <div class="pwm-ps-dialog-body">
                ${person.links?.length
                    ? html`<ul class="pwm-ps-links">
                          ${person.links.map(
                              (ref) => html`<li><a href=${ref.link}>${ref.name}</a></li>`,
                          )}
                      </ul>`
                    : nothing}
                <table class="pwm-ps-details-table">
                    <tbody>
                        ${Object.entries(person.detail ?? {}).map(
                            ([key, detail]) => html`
                                <tr>
                                    <td class="pwm-ps-detail-label">${detail.label}</td>
                                    <td>${this.renderDetailValue(key, detail)}</td>
                                </tr>
                            `,
                        )}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderDetailValue(_key: string, detail: PersonDetailAttribute): TemplateResult {
        if (detail.type === 'userDN') {
            return html`
                <ul class="pwm-ps-detail-list">
                    ${(detail.userReferences ?? []).map(
                        (user) => html`<li>
                            <a
                                href="#/details/${encodeURIComponent(user.userKey)}"
                                @click=${(e: Event) => {
                                    e.preventDefault();
                                    this.gotoRelated(user.userKey);
                                }}
                            >${user.displayName}</a>
                        </li>`,
                    )}
                </ul>
            `;
        }
        return html`
            <ul class="pwm-ps-detail-list">
                ${(detail.values ?? []).map(
                    (value) => html`<li>
                        ${detail.type === 'email'
                            ? html`<a href="mailto:${value}">${value}</a>`
                            : detail.type === 'tel'
                              ? html`<a href="tel:${value}">${value}</a>`
                              : html`<span>${value}</span>`}
                        ${detail.searchable
                            ? html`<button
                                      type="button"
                                      class="pwm-ps-search-link"
                                      title=${`Search for "${value}"`}
                                      @click=${() => this.searchFor(value)}
                                  >🔍</button>`
                            : nothing}
                    </li>`,
                )}
            </ul>
        `;
    }

    private renderExport(): TemplateResult {
        return html`
            <div class="pwm-ps-dialog-header"><div class="pwm-ps-header-main"><div class="pwm-ps-header-top">
                <h2>Export Org Chart</h2>
            </div></div></div>
            <div class="pwm-ps-dialog-body">
                <p>Export the org chart for ${this.person?.displayNames?.[0] ?? ''} to the depth selected below.</p>
                ${this.renderDepthSelect(this.config?.maxExportDepth ?? 1)}
            </div>
            <div class="pwm-ps-dialog-actions">
                <button type="button" class="pwm-ps-button" @click=${this.runExport}>Export</button>
                <button type="button" class="pwm-ps-button" @click=${() => { this.sub = 'none'; }}>Cancel</button>
            </div>
        `;
    }

    private renderEmail(): TemplateResult {
        return html`
            <div class="pwm-ps-dialog-header"><div class="pwm-ps-header-main"><div class="pwm-ps-header-top">
                <h2>Email Team</h2>
            </div></div></div>
            <div class="pwm-ps-dialog-body">
                <p>Email the team reporting to ${this.person?.displayNames?.[0] ?? ''} to the depth selected below.</p>
                ${this.renderDepthSelect(this.config?.maxEmailDepth ?? 1)}
                <div class="pwm-ps-dialog-field">
                    <label for="pwm-ps-emails">Recipients</label>
                    <textarea
                        id="pwm-ps-emails"
                        rows="4"
                        readonly
                        .value=${this.fetchingEmails ? 'Loading…' : this.teamEmails}
                    ></textarea>
                </div>
            </div>
            <div class="pwm-ps-dialog-actions">
                <button type="button" class="pwm-ps-button" ?disabled=${this.fetchingEmails || !this.teamEmails} @click=${this.sendEmail}>Send Email</button>
                <button type="button" class="pwm-ps-button" @click=${() => { this.sub = 'none'; }}>Cancel</button>
            </div>
        `;
    }

    private renderDepthSelect(maxDepth: number): TemplateResult {
        const options: number[] = [];
        for (let i = 1; i <= Math.max(1, maxDepth); i++) {
            options.push(i);
        }
        return html`
            <div class="pwm-ps-dialog-field">
                <label for="pwm-ps-depth">Depth</label>
                <select
                    id="pwm-ps-depth"
                    .value=${String(this.depth)}
                    @change=${(e: Event) => this.onDepthChange((e.target as HTMLSelectElement).value)}
                >
                    ${options.map((d) => html`<option value=${d}>${d}</option>`)}
                </select>
            </div>
        `;
    }
}

function cssUrl(value: string): string {
    return `'${value.replace(/'/g, "\\'")}'`;
}
