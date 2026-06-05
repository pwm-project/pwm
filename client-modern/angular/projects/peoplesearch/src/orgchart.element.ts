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
import { repeat } from 'lit/directives/repeat.js';
import {
    autoComplete,
    getDirectReports,
    getManagementChain,
    getOrgChartData,
    getPerson,
} from './services/people-service';
import {
    getOrgChartMaxParents,
    orgChartShowChildCount,
    photosEnabled as photosEnabledConfig,
    printingEnabled as printingEnabledConfig,
} from './services/config-service';
import { ajaxTypingWait } from './services/pwm-fetch';
import { getItem, setItem, StorageKeys } from './services/local-storage';
import { navigate } from './nav';
import { renderPersonCard } from './person-card';
import type { Person } from './models';

/**
 * Native port of the legacy {@code OrgChartSearchComponent} +
 * {@code OrgChartComponent} (issue #729, session 6).  Renders the management
 * chain (top), the focused person and assistant (middle), and direct reports
 * (bottom), with an autocomplete search box and view toggle to return to the
 * cards / table search views.
 *
 * <p>Simplification vs the legacy: managers render in a wrapping row rather
 * than the resize-driven "overflow ellipsis manager" collapse - the full chain
 * (up to {@code orgChartMaxParents}) is always shown; only the responsive
 * collapse-to-ellipsis behavior is dropped.  Clicking a manager / direct report
 * re-roots the chart; clicking the focused card opens that person's details.</p>
 */
@customElement('pwm-peoplesearch-orgchart')
export class OrgChartElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    /** Root person; empty string means "the current user" (backend resolves). */
    @property({ type: String }) userKey = '';

    @state() private person: Person | null = null;
    @state() private managementChain: Person[] = [];
    @state() private directReports: Person[] = [];
    @state() private assistant: Person | null = null;
    @state() private loading = true;
    @state() private errorMessage = '';

    @state() private query = '';
    @state() private suggestions: Person[] = [];
    @state() private showSuggestions = false;

    private photosEnabled = true;
    private showChildCount = false;
    private printEnabled = false;
    private maxParents = 10;

    private fetchController: AbortController | null = null;
    private acController: AbortController | null = null;
    private debounceMs = ajaxTypingWait();
    private debounceTimer: ReturnType<typeof setTimeout> | null = null;

    override connectedCallback(): void {
        super.connectedCallback();
        this.query = getItem(StorageKeys.SEARCH_TEXT) ?? '';
        void this.load();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        this.fetchController?.abort();
        this.acController?.abort();
        if (this.debounceTimer != null) {
            clearTimeout(this.debounceTimer);
        }
    }

    override updated(changed: Map<string, unknown>): void {
        if (changed.has('userKey')) {
            const prev = changed.get('userKey') as string | undefined;
            if (prev !== undefined && prev !== this.userKey) {
                void this.load();
            }
        }
    }

    private async load(): Promise<void> {
        this.fetchController?.abort();
        const controller = new AbortController();
        this.fetchController = controller;
        this.loading = true;
        this.errorMessage = '';
        this.managementChain = [];
        this.directReports = [];
        this.assistant = null;
        try {
            [this.photosEnabled, this.showChildCount, this.printEnabled, this.maxParents] = await Promise.all([
                photosEnabledConfig(controller.signal),
                orgChartShowChildCount(controller.signal),
                printingEnabledConfig(controller.signal),
                getOrgChartMaxParents(controller.signal),
            ]);

            const root = await getOrgChartData(this.userKey || undefined, true, controller.signal);
            if (controller.signal.aborted) {
                return;
            }
            const selfKey = root.self?.userKey ?? this.userKey;
            this.assistant = root.assistant ?? null;

            const [person, chain, reports] = await Promise.all([
                getPerson(selfKey, controller.signal),
                getManagementChain(selfKey, this.maxParents, controller.signal),
                getDirectReports(selfKey, controller.signal),
            ]);
            if (controller.signal.aborted) {
                return;
            }
            this.person = person;
            this.managementChain = chain;
            this.directReports = [...reports].sort(byDisplayName);
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

    // ---- search box ----

    private onQueryInput(value: string): void {
        this.query = value;
        setItem(StorageKeys.SEARCH_TEXT, value);
        if (this.debounceTimer != null) {
            clearTimeout(this.debounceTimer);
        }
        this.debounceTimer = setTimeout(() => void this.runAutoComplete(), this.debounceMs);
    }

    private async runAutoComplete(): Promise<void> {
        const trimmed = this.query.trim();
        if (!trimmed) {
            this.suggestions = [];
            this.showSuggestions = false;
            return;
        }
        this.acController?.abort();
        const controller = new AbortController();
        this.acController = controller;
        try {
            const results = await autoComplete(trimmed, controller.signal);
            if (controller.signal.aborted) {
                return;
            }
            this.suggestions = results;
            this.showSuggestions = results.length > 0;
        } catch {
            this.suggestions = [];
            this.showSuggestions = false;
        }
    }

    private selectSuggestion(person: Person): void {
        this.showSuggestions = false;
        this.query = '';
        setItem(StorageKeys.SEARCH_TEXT, '');
        if (person.userKey) {
            navigate(this, { kind: 'orgchart', key: person.userKey });
        }
    }

    // ---- render ----

    override render(): TemplateResult {
        return html`
            <div class="pwm-ps-header">
                <h2 id="page-content-title">Org Chart</h2>
                <div class="pwm-ps-search-wrap">
                    <input
                        type="search"
                        class="pwm-ps-search"
                        placeholder="Search"
                        autocomplete="off"
                        .value=${this.query}
                        @input=${(e: Event) => this.onQueryInput((e.target as HTMLInputElement).value)}
                        @focus=${() => { this.showSuggestions = this.suggestions.length > 0; }}
                    />
                    ${this.showSuggestions ? this.renderSuggestions() : nothing}
                </div>
                <span class="pwm-ps-spacer"></span>
                <button type="button" class="pwm-ps-icon-button" title="Card view" aria-label="Card view" @click=${() => navigate(this, { kind: 'search' })}>▦</button>
                ${this.printEnabled
                    ? html`<button type="button" class="pwm-ps-icon-button" title="Print" aria-label="Print" @click=${() => window.print()}>⎙</button>`
                    : nothing}
            </div>

            ${this.errorMessage
                ? html`<div class="pwm-ps-status" data-error="true">${this.errorMessage}</div>`
                : this.loading
                  ? html`<div class="pwm-ps-status">Loading…</div>`
                  : this.renderChart()}
        `;
    }

    private renderSuggestions(): TemplateResult {
        return html`
            <ul class="pwm-ps-autocomplete" role="listbox">
                ${this.suggestions.map(
                    (person) => html`<li
                        role="option"
                        tabindex="0"
                        @click=${() => this.selectSuggestion(person)}
                        @keydown=${(e: KeyboardEvent) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                this.selectSuggestion(person);
                            }
                        }}
                    >${person._displayName ?? person.displayNames?.[0] ?? ''}</li>`,
                )}
            </ul>
        `;
    }

    private renderChart(): TemplateResult {
        if (!this.person) {
            return html`<div class="pwm-ps-status">No org chart data.</div>`;
        }
        return html`
            <div class="pwm-ps-orgchart">
                ${this.managementChain.length
                    ? html`<div class="pwm-ps-managers" role="list">
                          ${repeat(
                              [...this.managementChain].reverse(),
                              (m) => m.userKey ?? '',
                              (m) => renderPersonCard(m, {
                                  photosEnabled: this.photosEnabled,
                                  secondaryLines: 1,
                                  size: 'small',
                                  onClick: (p) => this.reRoot(p),
                              }),
                          )}
                      </div>
                      <div class="pwm-ps-orgchart-connector" aria-hidden="true"></div>`
                    : nothing}

                <div class="pwm-ps-self-row">
                    ${renderPersonCard(this.person, {
                        photosEnabled: this.photosEnabled,
                        secondaryLines: 7,
                        size: 'large',
                        showReports: this.showChildCount,
                        onClick: (p) => this.openDetails(p),
                    })}
                    ${this.assistant
                        ? html`<div class="pwm-ps-assistant">
                              ${renderPersonCard(this.assistant, {
                                  photosEnabled: this.photosEnabled,
                                  secondaryLines: 1,
                                  size: 'small',
                                  onClick: (p) => this.reRoot(p),
                              })}
                          </div>`
                        : nothing}
                </div>

                ${this.directReports.length
                    ? html`<div class="pwm-ps-orgchart-connector" aria-hidden="true"></div>
                          <div class="pwm-ps-reports" role="list">
                              ${repeat(
                                  this.directReports,
                                  (r) => r.userKey ?? '',
                                  (r) => renderPersonCard(r, {
                                      photosEnabled: this.photosEnabled,
                                      secondaryLines: 3,
                                      showReports: this.showChildCount,
                                      onClick: (p) => this.reRoot(p),
                                  }),
                              )}
                          </div>`
                    : nothing}
            </div>
        `;
    }

    private reRoot(person: Person): void {
        if (person.userKey) {
            navigate(this, { kind: 'orgchart', key: person.userKey });
        }
    }

    private openDetails(person: Person): void {
        if (person.userKey) {
            navigate(this, { kind: 'details', key: person.userKey });
        }
    }
}

function byDisplayName(a: Person, b: Person): number {
    const an = a.displayNames?.[0] ?? '';
    const bn = b.displayNames?.[0] ?? '';
    return an.localeCompare(bn);
}
