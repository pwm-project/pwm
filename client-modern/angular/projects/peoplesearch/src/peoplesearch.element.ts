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
import { customElement, state } from 'lit/decorators.js';
import { repeat } from 'lit/directives/repeat.js';
import {
    search,
    advancedSearch,
    getPerson,
    type AdvancedSearchQuery,
} from './services/people-service';
import {
    advancedSearchConfig,
    defaultValueForAttribute,
    orgChartEnabled as orgChartEnabledConfig,
    photosEnabled as photosEnabledConfig,
    searchColumns,
    type AdvancedSearchConfig,
    type AttributeMetadata,
    type SearchColumns,
} from './services/config-service';
import { ajaxTypingWait } from './services/pwm-fetch';
import { getItem, setItem, StorageKeys } from './services/local-storage';
import { PS_NAVIGATE, type PsNavigateDetail } from './nav';
import { renderPersonCard } from './person-card';
import './orgchart.element';
import './person-details-dialog.element';
import type { Person, SearchResult } from './models';

type Mode = 'search' | 'orgchart';
type SearchView = 'cards' | 'table';

const VIEW_CARDS: SearchView = 'cards';
const VIEW_TABLE: SearchView = 'table';

function legacyViewToLocal(stored: string | null): SearchView {
    return stored === 'search.table' || stored === 'table' ? VIEW_TABLE : VIEW_CARDS;
}
function localViewToLegacyKey(view: SearchView): string {
    return view === VIEW_TABLE ? 'search.table' : 'search.cards';
}

/**
 * Peoplesearch migration (issue #729, session 6): the full module as a single
 * Lit custom element - cards + table search views, advanced search, the org
 * chart, and the person-details modal (with export / email-team).  Opt-in via
 * {@code ?modernUi=1}; the URL hash drives top-level mode
 * ({@code #/orgchart/<key>} =&gt; org chart, {@code #/details/<key>} =&gt;
 * details modal over search, otherwise search).
 *
 * <p>Simplification vs the legacy ui-router nesting: the person-details modal
 * always overlays the search view, so opening details from the org chart and
 * then closing returns to search (the modal's "Organizational Chart" button
 * re-opens the chart).  Light-DOM render root so PWM theme stylesheets cascade.</p>
 */
@customElement('pwm-peoplesearch')
export class PeopleSearchElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    // ---- routing ----
    @state() private mode: Mode = 'search';
    @state() private orgchartKey = '';
    @state() private detailsKey: string | null = null;

    // ---- search state ----
    @state() private query = '';
    @state() private queries: AdvancedSearchQuery[] = [];
    @state() private advancedMode = false;
    @state() private view: SearchView = VIEW_CARDS;
    @state() private status = '';
    @state() private error = '';
    @state() private searchResult: SearchResult | null = null;
    /**
     * Cards view only: the search response carries flat attributes but no
     * {@code displayNames}, so - like the legacy cards component - we enrich each
     * hit via a {@code detail} fetch to get the configured card labels.  The
     * table view renders the raw flat results directly, so it stays on
     * {@code searchResult}.
     */
    @state() private cardsPeople: Person[] = [];

    // ---- config ----
    @state() private advancedConfig: AdvancedSearchConfig | null = null;
    @state() private columns: SearchColumns = {};
    @state() private photosEnabled = true;
    @state() private orgChartAvailable = true;

    // ---- internal ----
    private debounceTimer: ReturnType<typeof setTimeout> | null = null;
    private currentSearch: AbortController | null = null;
    private cardsEnrich: AbortController | null = null;
    private debounceMs = ajaxTypingWait();
    private attributeMetadata: Record<string, AttributeMetadata> = {};
    private hashListener: (() => void) | null = null;
    private navListener: ((e: Event) => void) | null = null;

    override connectedCallback(): void {
        super.connectedCallback();
        this.debounceMs = ajaxTypingWait();
        const storedView = getItem(StorageKeys.SEARCH_VIEW);
        if (storedView) {
            this.view = legacyViewToLocal(storedView);
        }
        const storedQuery = getItem(StorageKeys.SEARCH_TEXT);
        if (storedQuery) {
            this.query = storedQuery;
        }
        this.applyHash(window.location.hash);
        this.hashListener = (): void => this.applyHash(window.location.hash);
        window.addEventListener('hashchange', this.hashListener);
        this.navListener = (e: Event): void => this.onNavigate(e as CustomEvent<PsNavigateDetail>);
        this.addEventListener(PS_NAVIGATE, this.navListener);
        void this.loadConfig();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        this.cancelDebounce();
        this.abortInFlight();
        this.abortCardsEnrich();
        if (this.hashListener) {
            window.removeEventListener('hashchange', this.hashListener);
            this.hashListener = null;
        }
        if (this.navListener) {
            this.removeEventListener(PS_NAVIGATE, this.navListener);
            this.navListener = null;
        }
    }

    // ---- routing ----

    private applyHash(hash: string): void {
        const orgMatch = /^#\/orgchart(?:\/(.+))?$/.exec(hash || '');
        const detailMatch = /^#\/details\/(.+)$/.exec(hash || '');
        if (orgMatch) {
            this.mode = 'orgchart';
            this.orgchartKey = orgMatch[1] ? decodeURIComponent(orgMatch[1]) : '';
            this.detailsKey = null;
        } else if (detailMatch) {
            this.mode = 'search';
            this.detailsKey = decodeURIComponent(detailMatch[1]);
        } else {
            this.mode = 'search';
            this.detailsKey = null;
        }
    }

    private onNavigate(event: CustomEvent<PsNavigateDetail>): void {
        const { kind, key, query } = event.detail;
        if (kind === 'details' && key) {
            window.location.hash = `#/details/${encodeURIComponent(key)}`;
        } else if (kind === 'orgchart') {
            window.location.hash = key ? `#/orgchart/${encodeURIComponent(key)}` : '#/orgchart';
        } else if (kind === 'search') {
            if (query != null) {
                this.advancedMode = false;
                this.query = query;
                setItem(StorageKeys.SEARCH_TEXT, query);
            }
            if (window.location.hash) {
                window.location.hash = '';
            } else {
                // Already on the search route - apply directly.
                this.applyHash('');
            }
            if (query != null) {
                this.kickOffSearch();
            }
        }
    }

    private async loadConfig(): Promise<void> {
        try {
            const [advConfig, cols, photosOn, orgChartOn] = await Promise.all([
                advancedSearchConfig(),
                searchColumns(),
                photosEnabledConfig(),
                orgChartEnabledConfig(),
            ]);
            this.advancedConfig = advConfig;
            this.columns = cols;
            this.photosEnabled = photosOn;
            this.orgChartAvailable = orgChartOn;
            this.attributeMetadata = {};
            for (const meta of advConfig.attributes) {
                this.attributeMetadata[meta.attribute] = meta;
            }
            if (this.mode === 'search' && this.query && !this.advancedMode) {
                this.kickOffSearch();
            }
        } catch (err) {
            // eslint-disable-next-line no-console
            console.warn('[pwm-peoplesearch] failed to load clientData config:', err);
            this.advancedConfig = { enabled: false, maxRows: 0, attributes: [] };
        }
    }

    // ============================================================================
    // RENDER
    // ============================================================================

    override render(): TemplateResult {
        if (this.mode === 'orgchart') {
            return html`<pwm-peoplesearch-orgchart .userKey=${this.orgchartKey}></pwm-peoplesearch-orgchart>`;
        }
        return html`
            ${this.renderSearchMode()}
            ${this.detailsKey
                ? html`<pwm-peoplesearch-details
                          .userKey=${this.detailsKey}
                          @dialog-close=${this.onDetailsClose}
                      ></pwm-peoplesearch-details>`
                : nothing}
        `;
    }

    private onDetailsClose = (): void => {
        window.location.hash = '';
    };

    private renderSearchMode(): TemplateResult {
        return html`
            <div class="pwm-ps-header">
                ${this.advancedMode ? this.renderAdvancedHeader() : this.renderSimpleHeader()}
                <span class="pwm-ps-spacer"></span>
                ${this.renderViewToggle()}
            </div>
            <div class="pwm-ps-status" data-error=${this.error ? 'true' : 'false'}>
                ${this.error || this.status || nothing}
            </div>
            ${this.view === VIEW_CARDS ? this.renderCards() : this.renderTable()}
        `;
    }

    private renderSimpleHeader(): TemplateResult {
        const advancedAvailable = Boolean(this.advancedConfig?.enabled);
        return html`
            <h2 id="page-content-title">People Search</h2>
            <input
                type="search"
                class="pwm-ps-search"
                placeholder="Search"
                autocomplete="off"
                autofocus
                .value=${this.query}
                @input=${this.onQueryInput}
            />
            ${advancedAvailable
                ? html`<button type="button" class="pwm-ps-icon-button" title="Advanced search" aria-label="Advanced search" @click=${this.enableAdvancedSearch}>+</button>`
                : nothing}
        `;
    }

    private renderAdvancedHeader(): TemplateResult {
        const canAdd = this.queries.length < (this.advancedConfig?.maxRows ?? 0);
        return html`
            <div class="pwm-ps-advanced">
                ${repeat(this.queries, (_q, i) => i, (q, i) => this.renderQueryRow(q, i))}
                ${canAdd
                    ? html`<button type="button" class="pwm-ps-icon-button" title="Add attribute" aria-label="Add attribute" @click=${this.addQueryRow}>+</button>`
                    : nothing}
                <button type="button" class="pwm-ps-icon-button" title="Close advanced search" aria-label="Close advanced search" @click=${this.disableAdvancedSearch}>&times;</button>
            </div>
        `;
    }

    private renderQueryRow(query: AdvancedSearchQuery, rowIndex: number): TemplateResult {
        const meta = this.attributeMetadata[query.key];
        const valueInput = meta?.type === 'select' && meta.options
            ? html`<select
                       class="pwm-ps-attr-value"
                       .value=${query.value}
                       @change=${(e: Event) => this.onAdvancedValueChange(rowIndex, (e.target as HTMLSelectElement).value)}
                   >
                       ${Object.entries(meta.options).map(([k, label]) => html`<option value=${k}>${label}</option>`)}
                   </select>`
            : html`<input
                       type="text"
                       class="pwm-ps-attr-value"
                       autocomplete="off"
                       .value=${query.value}
                       @input=${(e: Event) => this.onAdvancedValueInput(rowIndex, (e.target as HTMLInputElement).value)}
                   />`;
        return html`
            <div class="pwm-ps-attr-row">
                <select
                    class="pwm-ps-attr-key"
                    .value=${query.key}
                    @change=${(e: Event) => this.onAdvancedKeyChange(rowIndex, (e.target as HTMLSelectElement).value)}
                >
                    ${(this.advancedConfig?.attributes ?? []).map((a) => html`<option value=${a.attribute}>${a.label}</option>`)}
                </select>
                ${valueInput}
                <button type="button" class="pwm-ps-icon-button" title="Remove attribute" aria-label="Remove attribute" @click=${() => this.removeQueryRow(rowIndex)}>&times;</button>
            </div>
        `;
    }

    private renderViewToggle(): TemplateResult {
        return html`
            <div class="pwm-ps-view-toggle" role="group" aria-label="View mode">
                <button type="button" class="pwm-ps-icon-button" data-active=${this.view === VIEW_CARDS} title="Card view" aria-label="Card view" aria-pressed=${this.view === VIEW_CARDS} @click=${() => this.setView(VIEW_CARDS)}>▦</button>
                <button type="button" class="pwm-ps-icon-button" data-active=${this.view === VIEW_TABLE} title="List view" aria-label="List view" aria-pressed=${this.view === VIEW_TABLE} @click=${() => this.setView(VIEW_TABLE)}>☰</button>
                ${this.orgChartAvailable
                    ? html`<button type="button" class="pwm-ps-icon-button" title="Org chart" aria-label="Org chart" @click=${() => { window.location.hash = '#/orgchart'; }}>⛓</button>`
                    : nothing}
            </div>
        `;
    }

    private renderCards(): TemplateResult | typeof nothing {
        const people = this.sortedCardsPeople();
        if (people.length === 0) {
            return nothing;
        }
        return html`
            <div class="pwm-ps-cards" role="list">
                ${repeat(
                    people,
                    (p) => p.userKey ?? p._displayName ?? '',
                    (p) => renderPersonCard(p, {
                        photosEnabled: this.photosEnabled,
                        secondaryLines: 3,
                        onClick: (person) => this.openDetails(person),
                    }),
                )}
            </div>
        `;
    }

    private renderTable(): TemplateResult | typeof nothing {
        const people = this.sortedPeople();
        if (people.length === 0) {
            return nothing;
        }
        const colEntries = Object.entries(this.columns);
        const effectiveColumns: Array<[string, string]> = colEntries.length > 0
            ? colEntries
            : Object.keys(people[0] ?? {})
                  .filter((k) => !k.startsWith('_') && !['userKey', 'photoURL', 'displayNames', 'numDirectReports', 'detail', 'links'].includes(k))
                  .map((k) => [k, k] as [string, string]);
        return html`
            <table class="pwm-ps-table">
                <thead>
                    <tr>${effectiveColumns.map(([, label]) => html`<th>${label}</th>`)}</tr>
                </thead>
                <tbody>
                    ${repeat(
                        people,
                        (p) => p.userKey ?? '',
                        (p) => html`
                            <tr tabindex="0" @click=${() => this.openDetails(p)} @keydown=${(e: KeyboardEvent) => this.onRowKeydown(e, p)}>
                                ${effectiveColumns.map(([attr]) => html`<td>${formatCell((p as unknown as Record<string, unknown>)[attr])}</td>`)}
                            </tr>
                        `,
                    )}
                </tbody>
            </table>
        `;
    }

    private sortedPeople(): Person[] {
        const people = this.searchResult?.people ?? [];
        return [...people].sort((a, b) => (a.displayNames?.[0] ?? '').localeCompare(b.displayNames?.[0] ?? ''));
    }

    private sortedCardsPeople(): Person[] {
        return [...this.cardsPeople].sort((a, b) => (a.displayNames?.[0] ?? '').localeCompare(b.displayNames?.[0] ?? ''));
    }

    /**
     * Populate {@code cardsPeople} from the current search results by fetching
     * each hit's detail (the only response that carries {@code displayNames}).
     * Mirrors the legacy {@code PeopleSearchCardsComponent.onSearchResult}: each
     * detail request is abortable and results stream in as they resolve.
     */
    private enrichCards(): void {
        this.abortCardsEnrich();
        this.cardsPeople = [];
        const people = this.searchResult?.people ?? [];
        if (people.length === 0) {
            return;
        }
        const controller = new AbortController();
        this.cardsEnrich = controller;
        const signal = controller.signal;
        for (const hit of people) {
            if (!hit.userKey) {
                continue;
            }
            getPerson(hit.userKey, signal)
                .then((person) => {
                    if (signal.aborted || !person) {
                        return;
                    }
                    this.cardsPeople = [...this.cardsPeople, person];
                })
                .catch((err) => {
                    if ((err as { name?: string }).name === 'AbortError') {
                        return;
                    }
                    this.error = (err as Error).message;
                });
        }
    }

    private abortCardsEnrich(): void {
        if (this.cardsEnrich) {
            this.cardsEnrich.abort();
            this.cardsEnrich = null;
        }
    }

    // ============================================================================
    // EVENTS
    // ============================================================================

    private openDetails(person: Person): void {
        if (person.userKey) {
            window.location.hash = `#/details/${encodeURIComponent(person.userKey)}`;
        }
    }

    private onRowKeydown(event: KeyboardEvent, person: Person): void {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            this.openDetails(person);
        }
    }

    private onQueryInput = (event: Event): void => {
        const value = (event.target as HTMLInputElement).value;
        this.query = value;
        setItem(StorageKeys.SEARCH_TEXT, value);
        this.cancelDebounce();
        this.debounceTimer = setTimeout(() => this.kickOffSearch(), this.debounceMs);
    };

    private setView(next: SearchView): void {
        if (this.view === next) {
            return;
        }
        this.view = next;
        setItem(StorageKeys.SEARCH_VIEW, localViewToLegacyKey(next));
        if (next === VIEW_CARDS) {
            // Enrich on demand if we switched into cards after a search ran in
            // the table view (or have stale data from a prior result set).
            if ((this.searchResult?.people.length ?? 0) > 0 && this.cardsPeople.length === 0) {
                this.enrichCards();
            }
        } else {
            this.abortCardsEnrich();
        }
    }

    private enableAdvancedSearch = (): void => {
        this.clearSearchOutput();
        this.queries = [];
        this.addQueryRow();
        this.advancedMode = true;
    };

    private disableAdvancedSearch = (): void => {
        this.advancedMode = false;
        this.queries = [];
        this.clearSearchOutput();
        if (this.query) {
            this.kickOffSearch();
        }
    };

    private addQueryRow = (): void => {
        const meta = this.advancedConfig?.attributes[0];
        if (!meta) {
            return;
        }
        this.queries = [...this.queries, { key: meta.attribute, value: defaultValueForAttribute(meta) }];
        this.kickOffSearch();
    };

    private removeQueryRow(rowIndex: number): void {
        const next = [...this.queries];
        next.splice(rowIndex, 1);
        this.queries = next;
        if (next.length === 0) {
            this.disableAdvancedSearch();
        } else {
            this.kickOffSearch();
        }
    }

    private onAdvancedKeyChange(rowIndex: number, newKey: string): void {
        const meta = this.attributeMetadata[newKey];
        const next = [...this.queries];
        next[rowIndex] = { key: newKey, value: meta ? defaultValueForAttribute(meta) : '' };
        this.queries = next;
        this.kickOffSearch();
    }

    private onAdvancedValueChange(rowIndex: number, newValue: string): void {
        const next = [...this.queries];
        next[rowIndex] = { ...next[rowIndex], value: newValue };
        this.queries = next;
        this.kickOffSearch();
    }

    private onAdvancedValueInput = (rowIndex: number, newValue: string): void => {
        const next = [...this.queries];
        next[rowIndex] = { ...next[rowIndex], value: newValue };
        this.queries = next;
        this.cancelDebounce();
        this.debounceTimer = setTimeout(() => this.kickOffSearch(), this.debounceMs);
    };

    // ============================================================================
    // SEARCH DISPATCH
    // ============================================================================

    private cancelDebounce(): void {
        if (this.debounceTimer != null) {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = null;
        }
    }

    private abortInFlight(): void {
        if (this.currentSearch) {
            this.currentSearch.abort();
            this.currentSearch = null;
        }
    }

    private clearSearchOutput(): void {
        this.searchResult = null;
        this.status = '';
        this.error = '';
        this.abortInFlight();
        this.abortCardsEnrich();
        this.cardsPeople = [];
    }

    private validateAdvancedQueries(): string | null {
        const queries = this.queries;
        if (queries.length === 0 || (queries.length === 1 && !queries[0].key)) {
            return 'EMPTY';
        }
        const keys = new Set<string>();
        for (const q of queries) {
            keys.add(q.key);
        }
        if (keys.size < queries.length) {
            return 'Search attributes must be unique.';
        }
        return null;
    }

    private async kickOffSearch(): Promise<void> {
        this.abortInFlight();
        // Stop stale detail fetches from a superseded result set pushing cards.
        this.abortCardsEnrich();
        let request: Promise<SearchResult>;
        if (this.advancedMode) {
            const problem = this.validateAdvancedQueries();
            if (problem === 'EMPTY') {
                this.clearSearchOutput();
                return;
            }
            if (problem !== null) {
                this.status = problem;
                this.searchResult = null;
                return;
            }
            const controller = new AbortController();
            this.currentSearch = controller;
            this.error = '';
            this.status = 'Searching…';
            request = advancedSearch(this.queries, controller.signal);
        } else {
            const trimmed = this.query.trim();
            if (!trimmed) {
                this.clearSearchOutput();
                return;
            }
            const controller = new AbortController();
            this.currentSearch = controller;
            this.error = '';
            this.status = 'Searching…';
            request = search(trimmed, controller.signal);
        }

        try {
            const result = await request;
            if (this.currentSearch?.signal.aborted) {
                return;
            }
            this.searchResult = result;
            if (result.sizeExceeded) {
                this.status = 'Results exceeded the configured limit; refine your search.';
            } else if (result.people.length === 0) {
                this.status = 'No results.';
            } else {
                this.status = '';
            }
            // Cards need per-hit detail enrichment for displayNames; only pay
            // for it when the cards view is actually showing.
            if (this.view === VIEW_CARDS) {
                this.enrichCards();
            } else {
                this.abortCardsEnrich();
                this.cardsPeople = [];
            }
        } catch (err) {
            if ((err as { name?: string }).name === 'AbortError') {
                return;
            }
            this.error = (err as Error).message;
            this.searchResult = null;
        } finally {
            this.currentSearch = null;
        }
    }
}

function formatCell(value: unknown): string {
    if (value == null) {
        return '';
    }
    if (Array.isArray(value)) {
        return value.join(', ');
    }
    return String(value);
}
