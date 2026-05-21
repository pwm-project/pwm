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
import { search, advancedSearch, type AdvancedSearchQuery } from './services/helpdesk-service';
import {
    advancedSearchConfig,
    defaultValueForAttribute,
    searchColumns,
    type AdvancedSearchConfig,
    type AttributeMetadata,
    type SearchColumns,
} from './services/config-service';
import { ajaxTypingWait } from './services/pwm-fetch';
import { getItem, setItem, StorageKeys } from './services/local-storage';
import type { Person, SearchResult } from './models';

/** Search-page view mode persisted in {@code HELPDESK_SEARCH_VIEW}. */
type ViewMode = 'search.cards' | 'search.table';

const VIEW_CARDS: ViewMode = 'search.cards';
const VIEW_TABLE: ViewMode = 'search.table';

/**
 * Session-2 helpdesk migration slice (issue #729): adds the table view, the
 * cards/table view toggle, and advanced (multi-attribute) search to the
 * session-1 cards-view foundation.  Still opt-in via {@code ?modernUi=1};
 * sessions 3-5 add the detail page, verification dialogs, and parity flip.
 *
 * <p>The element renders into its own light DOM (no shadow) so PWM's theme
 * stylesheets continue to cascade in.  Lit handles change detection and
 * templating; nothing else.</p>
 */
@customElement('pwm-helpdesk')
export class HelpdeskElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    // ---- search state ----
    @state() private query: string = '';
    @state() private queries: AdvancedSearchQuery[] = [];
    @state() private advancedMode: boolean = false;
    @state() private view: ViewMode = VIEW_CARDS;
    @state() private status: string = '';
    @state() private error: string = '';
    @state() private searchResult: SearchResult | null = null;

    // ---- config-loaded state ----
    @state() private advancedConfig: AdvancedSearchConfig | null = null;
    @state() private columns: SearchColumns = {};

    // ---- internal ----
    private debounceTimer: ReturnType<typeof setTimeout> | null = null;
    private currentSearch: AbortController | null = null;
    private debounceMs: number = 700;
    /** Lookup table for advanced-search attribute metadata by attribute name. */
    private attributeMetadata: Record<string, AttributeMetadata> = {};

    override connectedCallback(): void {
        super.connectedCallback();
        this.debounceMs = ajaxTypingWait();
        this.restorePersistedState();
        this.loadConfig();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        this.cancelDebounce();
        this.abortInFlight();
    }

    // ---- persistence ----

    /**
     * Restore the view mode and last-used query from sessionStorage using the
     * same keys the legacy bundle uses, so switching back and forth between
     * AngularJS and modern surfaces during the migration is seamless.
     */
    private restorePersistedState(): void {
        const storedView = getItem(StorageKeys.HELPDESK_SEARCH_VIEW);
        if (storedView === VIEW_TABLE || storedView === VIEW_CARDS) {
            this.view = storedView;
        }
        const storedQuery = getItem(StorageKeys.HELPDESK_SEARCH_TEXT);
        if (storedQuery) {
            this.query = storedQuery;
            // Trigger an initial search when the config has loaded - see
            // loadConfig() below; we wait so any advanced-search restoration
            // happens first.
        }
    }

    private async loadConfig(): Promise<void> {
        try {
            const [advConfig, cols] = await Promise.all([advancedSearchConfig(), searchColumns()]);
            this.advancedConfig = advConfig;
            this.columns = cols;
            this.attributeMetadata = {};
            for (const meta of advConfig.attributes) {
                this.attributeMetadata[meta.attribute] = meta;
            }
            // Kick off the initial search now that the config is in - simple-
            // mode restores its query string from sessionStorage, advanced mode
            // starts blank (legacy did not persist the per-row queries).
            if (this.query && !this.advancedMode) {
                this.kickOffSearch();
            }
        } catch (err) {
            // A clientData failure is not fatal - the simple-search code path
            // still works; we just won't have advanced search or column config.
            // eslint-disable-next-line no-console
            console.warn('[pwm-helpdesk] failed to load clientData config:', err);
            this.advancedConfig = { enabled: false, maxRows: 0, attributes: [] };
        }
    }

    // ---- render ----

    override render(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-header">
                ${this.advancedMode ? this.renderAdvancedHeader() : this.renderSimpleHeader()}
                <span class="pwm-helpdesk-spacer"></span>
                ${this.renderViewToggle()}
            </div>

            <div class="pwm-helpdesk-status" data-error=${this.error ? 'true' : 'false'}>
                ${this.error || this.status || nothing}
            </div>

            ${this.view === VIEW_CARDS ? this.renderCards() : this.renderTable()}
        `;
    }

    private renderSimpleHeader(): TemplateResult {
        const advancedAvailable = Boolean(this.advancedConfig?.enabled);
        return html`
            <h2 id="page-content-title">Help Desk</h2>
            <input
                type="search"
                class="pwm-helpdesk-search"
                placeholder="Search"
                autocomplete="off"
                autofocus
                .value=${this.query}
                @input=${this.onQueryInput}
            />
            ${advancedAvailable
                ? html`<button
                          type="button"
                          class="pwm-helpdesk-icon-button"
                          title="Advanced search"
                          aria-label="Advanced search"
                          @click=${this.enableAdvancedSearch}
                      >
                          +
                      </button>`
                : nothing}
        `;
    }

    private renderAdvancedHeader(): TemplateResult {
        const canAdd = this.queries.length < (this.advancedConfig?.maxRows ?? 0);
        return html`
            <div class="pwm-helpdesk-advanced">
                ${repeat(
                    this.queries,
                    (_q, i) => i,
                    (q, i) => this.renderQueryRow(q, i),
                )}
                ${canAdd
                    ? html`<button
                              type="button"
                              class="pwm-helpdesk-icon-button"
                              title="Add attribute"
                              aria-label="Add attribute"
                              @click=${this.addQueryRow}
                          >
                              +
                          </button>`
                    : nothing}
                <button
                    type="button"
                    class="pwm-helpdesk-icon-button"
                    title="Close advanced search"
                    aria-label="Close advanced search"
                    @click=${this.disableAdvancedSearch}
                >
                    &times;
                </button>
            </div>
        `;
    }

    private renderQueryRow(query: AdvancedSearchQuery, rowIndex: number): TemplateResult {
        const meta = this.attributeMetadata[query.key];
        const valueInput = meta?.type === 'select' && meta.options
            ? html`<select
                       class="pwm-helpdesk-attr-value"
                       .value=${query.value}
                       @change=${(e: Event) => this.onAdvancedValueChange(rowIndex, (e.target as HTMLSelectElement).value)}
                   >
                       ${Object.entries(meta.options).map(
                           ([k, label]) => html`<option value=${k}>${label}</option>`,
                       )}
                   </select>`
            : html`<input
                       type="text"
                       class="pwm-helpdesk-attr-value"
                       autocomplete="off"
                       .value=${query.value}
                       @input=${(e: Event) => this.onAdvancedValueInput(rowIndex, (e.target as HTMLInputElement).value)}
                   />`;
        return html`
            <div class="pwm-helpdesk-attr-row">
                <select
                    class="pwm-helpdesk-attr-key"
                    .value=${query.key}
                    @change=${(e: Event) => this.onAdvancedKeyChange(rowIndex, (e.target as HTMLSelectElement).value)}
                >
                    ${(this.advancedConfig?.attributes ?? []).map(
                        (a) => html`<option value=${a.attribute}>${a.label}</option>`,
                    )}
                </select>
                ${valueInput}
                <button
                    type="button"
                    class="pwm-helpdesk-icon-button"
                    title="Remove attribute"
                    aria-label="Remove attribute"
                    @click=${() => this.removeQueryRow(rowIndex)}
                >
                    &times;
                </button>
            </div>
        `;
    }

    private renderViewToggle(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-view-toggle" role="group" aria-label="View mode">
                <button
                    type="button"
                    class="pwm-helpdesk-icon-button"
                    data-active=${this.view === VIEW_CARDS}
                    title="Card view"
                    aria-label="Card view"
                    aria-pressed=${this.view === VIEW_CARDS}
                    @click=${() => this.setView(VIEW_CARDS)}
                >
                    ▦
                </button>
                <button
                    type="button"
                    class="pwm-helpdesk-icon-button"
                    data-active=${this.view === VIEW_TABLE}
                    title="List view"
                    aria-label="List view"
                    aria-pressed=${this.view === VIEW_TABLE}
                    @click=${() => this.setView(VIEW_TABLE)}
                >
                    ☰
                </button>
            </div>
        `;
    }

    private renderCards(): TemplateResult | typeof nothing {
        const people = this.searchResult?.people ?? [];
        if (people.length === 0) {
            return nothing;
        }
        return html`
            <div class="pwm-helpdesk-cards" role="list">
                ${repeat(
                    people,
                    (p) => p.userKey ?? p._displayName ?? '',
                    (p) => this.renderCard(p),
                )}
            </div>
        `;
    }

    private renderCard(person: Person): TemplateResult {
        const lines = person.displayNames ?? this.fallbackLines(person);
        const head = lines[0] ?? person._displayName ?? person.userKey ?? '';
        const rest = lines.slice(1, 4);
        const avatarStyle = person.photoURL ? `background-image:url(${cssUrl(person.photoURL)})` : '';
        return html`
            <div
                class="pwm-helpdesk-card"
                role="listitem"
                tabindex="0"
                @click=${() => this.onSelectPerson(person)}
                @keydown=${(e: KeyboardEvent) => this.onCardKeydown(e, person)}
            >
                <div class="pwm-helpdesk-card-avatar" style=${avatarStyle} aria-hidden="true"></div>
                <div class="pwm-helpdesk-card-body">
                    <h3 class="pwm-helpdesk-card-name" title=${head}>${head}</h3>
                    ${rest.map(
                        (line) => html`<div class="pwm-helpdesk-card-line" title=${line}>${line}</div>`,
                    )}
                </div>
            </div>
        `;
    }

    private renderTable(): TemplateResult | typeof nothing {
        const people = this.searchResult?.people ?? [];
        if (people.length === 0) {
            return nothing;
        }
        const colEntries = Object.entries(this.columns);
        const effectiveColumns: Array<[string, string]> = colEntries.length > 0
            ? colEntries
            // Fallback if config did not deliver columns: pick all non-internal
            // keys observed on the first row, in insertion order.
            : Object.keys(people[0] ?? {})
                  .filter((k) => !k.startsWith('_') && k !== 'userKey' && k !== 'id' && k !== 'photoURL' && k !== 'displayNames' && k !== 'numDirectReports' && k !== 'detail' && k !== 'links')
                  .map((k) => [k, k] as [string, string]);

        return html`
            <table class="pwm-helpdesk-table">
                <thead>
                    <tr>
                        ${effectiveColumns.map(([_attr, label]) => html`<th>${label}</th>`)}
                    </tr>
                </thead>
                <tbody>
                    ${repeat(
                        people,
                        (p) => p.userKey ?? '',
                        (p) => html`
                            <tr
                                tabindex="0"
                                @click=${() => this.onSelectPerson(p)}
                                @keydown=${(e: KeyboardEvent) => this.onCardKeydown(e, p)}
                            >
                                ${effectiveColumns.map(
                                    ([attr]) => html`<td>${formatCell((p as unknown as Record<string, unknown>)[attr])}</td>`,
                                )}
                            </tr>
                        `,
                    )}
                </tbody>
            </table>
        `;
    }

    /**
     * For search responses that omit {@code displayNames}, fall back to common
     * attribute fields so the card still has something useful in it.  PWM's
     * helpdesk search response is admin-configurable; subsequent sessions will
     * iterate all configured columns instead of guessing.
     */
    private fallbackLines(person: Person): string[] {
        const lines: string[] = [];
        if (person._displayName) {
            lines.push(person._displayName);
        } else if (person.givenName || person.sn) {
            lines.push([person.givenName, person.sn].filter(Boolean).join(' '));
        }
        if (person.title) {
            lines.push(person.title);
        }
        if (person.mail) {
            lines.push(person.mail);
        }
        if (person.telephoneNumber) {
            lines.push(person.telephoneNumber);
        }
        return lines;
    }

    // ---- event handlers ----

    private onQueryInput = (event: Event): void => {
        const value = (event.target as HTMLInputElement).value;
        this.query = value;
        setItem(StorageKeys.HELPDESK_SEARCH_TEXT, value);
        this.cancelDebounce();
        this.debounceTimer = setTimeout(() => this.kickOffSearch(), this.debounceMs);
    };

    private setView(next: ViewMode): void {
        if (this.view === next) {
            return;
        }
        this.view = next;
        setItem(StorageKeys.HELPDESK_SEARCH_VIEW, next);
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
        this.queries = [
            ...this.queries,
            { key: meta.attribute, value: defaultValueForAttribute(meta) },
        ];
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
        const newQuery: AdvancedSearchQuery = {
            key: newKey,
            value: meta ? defaultValueForAttribute(meta) : '',
        };
        const next = [...this.queries];
        next[rowIndex] = newQuery;
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

    private onSelectPerson(person: Person): void {
        // Session 1-2 stub: the helpdesk detail page is not yet migrated.  Send
        // the user back to the legacy AngularJS detail route by dropping the
        // modernUi flag from the URL.
        if (person.userKey) {
            window.location.hash = `#/details/${encodeURIComponent(person.userKey)}`;
            const url = new URL(window.location.href);
            url.searchParams.delete('modernUi');
            window.location.assign(url.toString());
        }
    }

    private onCardKeydown(event: KeyboardEvent, person: Person): void {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            this.onSelectPerson(person);
        }
    }

    // ---- search dispatch ----

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

        // Decide what request to fire (or skip).
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

/**
 * Render a cell value defensively.  PWM's search columns can contain strings,
 * arrays (multi-valued attributes), or null - normalize them all to a single
 * displayable string.
 */
function formatCell(value: unknown): string {
    if (value == null) {
        return '';
    }
    if (Array.isArray(value)) {
        return value.join(', ');
    }
    return String(value);
}

/**
 * Conservative CSS url() escaping for an attribute value we are interpolating
 * into a style string.
 */
function cssUrl(value: string): string {
    return `'${value.replace(/'/g, "\\'")}'`;
}
