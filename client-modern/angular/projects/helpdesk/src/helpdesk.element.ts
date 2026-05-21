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
    getPersonCard,
    unlockIntruder,
    clearOtpSecret,
    clearResponses,
    deleteUser,
    customAction,
    type AdvancedSearchQuery,
    type DetailAttribute,
    type PersonCard,
    type PersonDetail,
} from './services/helpdesk-service';
import {
    advancedSearchConfig,
    customActionButtons,
    defaultValueForAttribute,
    photosEnabled as photosEnabledConfig,
    searchColumns,
    type AdvancedSearchConfig,
    type AttributeMetadata,
    type CustomActionButton,
    type SearchColumns,
} from './services/config-service';
import { ajaxTypingWait } from './services/pwm-fetch';
import { getItem, setItem, StorageKeys } from './services/local-storage';
import type { Person, SearchResult } from './models';

/** Top-level mode: search page vs detail page (driven by URL hash). */
type Mode = 'search' | 'detail';

/** Search-page sub-view persisted in {@code HELPDESK_SEARCH_VIEW}. */
type SearchView = 'cards' | 'table';

/** Detail-page tab. */
type DetailTab = 'profile' | 'status' | 'history' | 'password' | 'security';

const VIEW_CARDS: SearchView = 'cards';
const VIEW_TABLE: SearchView = 'table';

/** Mapping from legacy ui-router state name to our local view enum. */
function legacyViewToLocal(stored: string | null): SearchView {
    if (stored === 'search.table' || stored === 'table') {
        return VIEW_TABLE;
    }
    return VIEW_CARDS;
}
function localViewToLegacyKey(view: SearchView): string {
    return view === VIEW_TABLE ? 'search.table' : 'search.cards';
}

/**
 * Sessions 1-3 of the helpdesk migration (issue #729): the entire search page
 * (cards + table views, advanced search) plus the detail page (attribute tabs,
 * simple action buttons).  Change Password and Verify still hand off to the
 * legacy bundle until session 4.  Opt-in via {@code ?modernUi=1}.
 *
 * <p>Light-DOM render root so PWM's theme stylesheets cascade in.  Lit handles
 * change detection and templating; nothing else.  URL hash drives the top-
 * level mode ({@code #/details/<userKey>} =&gt; detail; otherwise search).</p>
 */
@customElement('pwm-helpdesk')
export class HelpdeskElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    // ---- mode / routing ----
    @state() private mode: Mode = 'search';
    @state() private detailUserKey: string | null = null;

    // ---- search state ----
    @state() private query: string = '';
    @state() private queries: AdvancedSearchQuery[] = [];
    @state() private advancedMode: boolean = false;
    @state() private view: SearchView = VIEW_CARDS;
    @state() private status: string = '';
    @state() private error: string = '';
    @state() private searchResult: SearchResult | null = null;

    // ---- detail state ----
    @state() private personDetail: PersonDetail | null = null;
    @state() private personCard: PersonCard | null = null;
    @state() private detailTab: DetailTab = 'profile';
    @state() private detailLoading: boolean = false;
    @state() private detailError: string = '';
    @state() private actionMessage: string = '';

    // ---- config-loaded state ----
    @state() private advancedConfig: AdvancedSearchConfig | null = null;
    @state() private columns: SearchColumns = {};
    @state() private customButtons: Record<string, CustomActionButton> = {};
    @state() private photosEnabled: boolean = true;

    // ---- internal ----
    private debounceTimer: ReturnType<typeof setTimeout> | null = null;
    private currentSearch: AbortController | null = null;
    private currentDetailFetch: AbortController | null = null;
    private debounceMs: number = 700;
    private attributeMetadata: Record<string, AttributeMetadata> = {};
    private hashListener: (() => void) | null = null;

    override connectedCallback(): void {
        super.connectedCallback();
        this.debounceMs = ajaxTypingWait();
        this.restorePersistedState();
        this.applyHash(window.location.hash);
        this.hashListener = (): void => this.applyHash(window.location.hash);
        window.addEventListener('hashchange', this.hashListener);
        this.loadConfig();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        this.cancelDebounce();
        this.abortInFlight();
        if (this.currentDetailFetch) {
            this.currentDetailFetch.abort();
            this.currentDetailFetch = null;
        }
        if (this.hashListener) {
            window.removeEventListener('hashchange', this.hashListener);
            this.hashListener = null;
        }
    }

    // ---- mode / routing ----

    /**
     * Parse the URL hash and switch modes accordingly.  Pattern
     * {@code #/details/<userKey>} matches the legacy ui-router state name so
     * deep links from email notifications / bookmarked URLs still land in the
     * right place.
     */
    private applyHash(hash: string): void {
        const match = /^#\/details\/(.+)$/.exec(hash || '');
        if (match) {
            const userKey = decodeURIComponent(match[1]);
            this.mode = 'detail';
            this.detailUserKey = userKey;
            this.actionMessage = '';
            this.loadDetail(userKey);
        } else {
            this.mode = 'search';
            this.detailUserKey = null;
            this.personDetail = null;
            this.personCard = null;
            this.detailError = '';
        }
    }

    // ---- persistence ----

    private restorePersistedState(): void {
        const storedView = getItem(StorageKeys.HELPDESK_SEARCH_VIEW);
        if (storedView) {
            this.view = legacyViewToLocal(storedView);
        }
        const storedQuery = getItem(StorageKeys.HELPDESK_SEARCH_TEXT);
        if (storedQuery) {
            this.query = storedQuery;
        }
    }

    private async loadConfig(): Promise<void> {
        try {
            const [advConfig, cols, buttons, photosOn] = await Promise.all([
                advancedSearchConfig(),
                searchColumns(),
                customActionButtons(),
                photosEnabledConfig(),
            ]);
            this.advancedConfig = advConfig;
            this.columns = cols;
            this.customButtons = buttons;
            this.photosEnabled = photosOn;
            this.attributeMetadata = {};
            for (const meta of advConfig.attributes) {
                this.attributeMetadata[meta.attribute] = meta;
            }
            // Kick off any pending initial search now that config is in.
            if (this.mode === 'search' && this.query && !this.advancedMode) {
                this.kickOffSearch();
            }
        } catch (err) {
            // eslint-disable-next-line no-console
            console.warn('[pwm-helpdesk] failed to load clientData config:', err);
            this.advancedConfig = { enabled: false, maxRows: 0, attributes: [] };
        }
    }

    // ============================================================================
    // RENDER
    // ============================================================================

    override render(): TemplateResult {
        return this.mode === 'detail' ? this.renderDetailMode() : this.renderSearchMode();
    }

    // ----- search mode -----

    private renderSearchMode(): TemplateResult {
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
        const avatarStyle = this.photosEnabled && person.photoURL
            ? `background-image:url(${cssUrl(person.photoURL)})`
            : '';
        return html`
            <div
                class="pwm-helpdesk-card"
                role="listitem"
                tabindex="0"
                @click=${() => this.onSelectPerson(person)}
                @keydown=${(e: KeyboardEvent) => this.onCardKeydown(e, person)}
            >
                ${this.photosEnabled
                    ? html`<div class="pwm-helpdesk-card-avatar" style=${avatarStyle} aria-hidden="true"></div>`
                    : nothing}
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

    // ----- detail mode -----

    private renderDetailMode(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-detail-header">
                <h2 id="page-content-title">Help Desk</h2>
                <span class="pwm-helpdesk-spacer"></span>
                <button
                    type="button"
                    class="pwm-helpdesk-icon-button"
                    title="Refresh"
                    aria-label="Refresh"
                    @click=${this.refreshDetail}
                >
                    ⟳
                </button>
                <button
                    type="button"
                    class="pwm-helpdesk-icon-button"
                    title="Back to search"
                    aria-label="Back to search"
                    @click=${this.gotoSearch}
                >
                    &times;
                </button>
            </div>

            ${this.renderPersonCardHeader()}

            <div class="pwm-helpdesk-status" data-error=${this.detailError ? 'true' : 'false'}>
                ${this.detailError || this.actionMessage || (this.detailLoading ? 'Loading…' : nothing)}
            </div>

            ${this.personDetail
                ? html`
                      <div class="pwm-helpdesk-detail-buttons">
                          ${this.renderDetailActions()}
                      </div>
                      <div class="pwm-helpdesk-tabset">
                          ${this.renderDetailTabs()}
                      </div>
                      <div class="pwm-helpdesk-tab-panes">
                          ${this.renderActiveTab()}
                      </div>
                  `
                : nothing}
        `;
    }

    private renderPersonCardHeader(): TemplateResult | typeof nothing {
        const card = this.personCard;
        if (!card) {
            return nothing;
        }
        const lines = card.displayNames ?? [];
        const head = lines[0] ?? card.userKey ?? '';
        const rest = lines.slice(1, 4);
        const avatarStyle = this.photosEnabled && card.photoURL
            ? `background-image:url(${cssUrl(card.photoURL)})`
            : '';
        return html`
            <div class="pwm-helpdesk-secondary-header">
                <div class="pwm-helpdesk-detail-card">
                    ${this.photosEnabled
                        ? html`<div class="pwm-helpdesk-card-avatar" style=${avatarStyle} aria-hidden="true"></div>`
                        : nothing}
                    <div class="pwm-helpdesk-card-body">
                        <h3 class="pwm-helpdesk-card-name" title=${head}>${head}</h3>
                        ${rest.map(
                            (line) => html`<div class="pwm-helpdesk-card-line" title=${line}>${line}</div>`,
                        )}
                    </div>
                </div>
            </div>
        `;
    }

    private renderDetailActions(): TemplateResult {
        const person = this.personDetail;
        if (!person) {
            return html``;
        }
        return html`
            ${this.renderActionButton('changePassword', 'Change Password', () => this.changePasswordHandoff(), person)}
            ${this.renderActionButton('unlock', 'Unlock', () => this.confirmAction(
                `Unlock ${this.displayLabel()}?`,
                () => unlockIntruder(this.detailUserKey!),
            ), person)}
            ${this.renderActionButton('clearResponses', 'Clear Responses', () => this.confirmAction(
                `Clear security responses for ${this.displayLabel()}?`,
                () => clearResponses(this.detailUserKey!),
            ), person)}
            ${this.renderActionButton('clearOtpSecret', 'Clear OTP Secret', () => this.confirmAction(
                `Clear OTP secret for ${this.displayLabel()}?  The user will need to re-enroll.`,
                () => clearOtpSecret(this.detailUserKey!),
            ), person)}
            ${this.renderActionButton('verification', 'Verify', () => this.verifyHandoff(), person)}
            ${this.renderActionButton('deleteUser', 'Delete', () => this.confirmAction(
                `Permanently delete ${this.displayLabel()}?  This cannot be undone.`,
                () => deleteUser(this.detailUserKey!),
                () => this.gotoSearch(),
            ), person)}
            ${Object.entries(this.customButtons).map(
                ([label, btn]) => html`
                    <button
                        type="button"
                        class="pwm-helpdesk-action-button"
                        title=${btn.description ?? ''}
                        @click=${() => this.confirmAction(
                            `Run "${btn.name}" on ${this.displayLabel()}?`,
                            () => customAction(btn.name, this.detailUserKey!),
                        )}
                    >${btn.name ?? label}</button>
                `,
            )}
        `;
    }

    private renderActionButton(
        buttonName: string,
        label: string,
        handler: () => void,
        person: PersonDetail,
    ): TemplateResult | typeof nothing {
        const visible = (person.visibleButtons ?? []).includes(buttonName);
        if (!visible) {
            return nothing;
        }
        const enabled = (person.enabledButtons ?? []).includes(buttonName);
        return html`
            <button
                type="button"
                class="pwm-helpdesk-action-button"
                ?disabled=${!enabled}
                @click=${handler}
            >${label}</button>
        `;
    }

    private renderDetailTabs(): TemplateResult {
        const person = this.personDetail;
        if (!person) {
            return html``;
        }
        const tabs: Array<[DetailTab, string, boolean]> = [
            ['profile', 'Profile', true],
            ['status', 'Status', Boolean(person.statusData?.length)],
            ['history', 'History', Boolean(person.userHistory?.length)],
            ['password', 'Password Policy', true],
            ['security', 'Security Responses', Boolean(person.helpdeskResponses?.length)],
        ];
        return html`
            ${tabs
                .filter(([_t, _l, show]) => show)
                .map(
                    ([tab, label]) => html`
                        <button
                            type="button"
                            class="pwm-helpdesk-tab"
                            data-active=${this.detailTab === tab}
                            aria-pressed=${this.detailTab === tab}
                            @click=${() => {
                                this.detailTab = tab as DetailTab;
                            }}
                        >${label}</button>
                    `,
                )}
        `;
    }

    private renderActiveTab(): TemplateResult {
        const person = this.personDetail;
        if (!person) {
            return html``;
        }
        switch (this.detailTab) {
            case 'profile':
                return this.renderAttributeTable(person.profileData ?? []);
            case 'status':
                return this.renderAttributeTable(person.statusData ?? []);
            case 'history':
                return this.renderHistoryTable(person.userHistory ?? []);
            case 'password':
                return this.renderPasswordPolicyTable(person);
            case 'security':
                return this.renderAttributeTable(person.helpdeskResponses ?? []);
            default:
                return html``;
        }
    }

    private renderAttributeTable(items: DetailAttribute[]): TemplateResult {
        if (items.length === 0) {
            return html`<div class="pwm-helpdesk-empty">(no data)</div>`;
        }
        return html`
            <table class="pwm-helpdesk-details-table">
                <tbody>
                    ${items.map(
                        (item) => html`
                            <tr>
                                <td class="pwm-helpdesk-detail-label">${item.label}</td>
                                <td>${this.renderAttributeValue(item)}</td>
                            </tr>
                        `,
                    )}
                </tbody>
            </table>
        `;
    }

    private renderAttributeValue(item: DetailAttribute): TemplateResult | string {
        if (item.type === 'multiString') {
            return html`${(item.values ?? []).map((v) => html`<div>${v}</div>`)}`;
        }
        if (item.type === 'timestamp' && item.value) {
            return formatTimestamp(item.value);
        }
        return item.value ?? '';
    }

    private renderHistoryTable(items: PersonDetail['userHistory']): TemplateResult {
        const safe = items ?? [];
        if (safe.length === 0) {
            return html`<div class="pwm-helpdesk-empty">(no history)</div>`;
        }
        return html`
            <table class="pwm-helpdesk-details-table">
                <tbody>
                    ${safe.map(
                        (entry) => html`
                            <tr>
                                <td class="pwm-helpdesk-detail-label">${formatTimestamp(entry.timestamp)}</td>
                                <td>${entry.label}</td>
                            </tr>
                        `,
                    )}
                </tbody>
            </table>
        `;
    }

    private renderPasswordPolicyTable(person: PersonDetail): TemplateResult {
        return html`
            <table class="pwm-helpdesk-details-table">
                <tbody>
                    ${person.passwordPolicyDN
                        ? html`<tr>
                              <td class="pwm-helpdesk-detail-label">Policy</td>
                              <td>${person.passwordPolicyDN}</td>
                          </tr>`
                        : nothing}
                    ${person.passwordPolicyID
                        ? html`<tr>
                              <td class="pwm-helpdesk-detail-label">Profile</td>
                              <td>${person.passwordPolicyID}</td>
                          </tr>`
                        : nothing}
                    ${person.passwordRequirements?.length
                        ? html`<tr>
                              <td class="pwm-helpdesk-detail-label">Display</td>
                              <td>
                                  <ul>
                                      ${person.passwordRequirements.map((r) => html`<li>${r}</li>`)}
                                  </ul>
                              </td>
                          </tr>`
                        : nothing}
                    ${Object.entries(person.passwordPolicyRules ?? {}).map(
                        ([k, v]) => html`<tr>
                            <td class="pwm-helpdesk-detail-label">${k}</td>
                            <td>${v}</td>
                        </tr>`,
                    )}
                </tbody>
            </table>
        `;
    }

    // ============================================================================
    // EVENT HANDLERS
    // ============================================================================

    private onQueryInput = (event: Event): void => {
        const value = (event.target as HTMLInputElement).value;
        this.query = value;
        setItem(StorageKeys.HELPDESK_SEARCH_TEXT, value);
        this.cancelDebounce();
        this.debounceTimer = setTimeout(() => this.kickOffSearch(), this.debounceMs);
    };

    private setView(next: SearchView): void {
        if (this.view === next) {
            return;
        }
        this.view = next;
        setItem(StorageKeys.HELPDESK_SEARCH_VIEW, localViewToLegacyKey(next));
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
        if (person.userKey) {
            window.location.hash = `#/details/${encodeURIComponent(person.userKey)}`;
        }
    }

    private onCardKeydown(event: KeyboardEvent, person: Person): void {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            this.onSelectPerson(person);
        }
    }

    // ---- detail actions ----

    private refreshDetail = (): void => {
        if (this.detailUserKey) {
            this.loadDetail(this.detailUserKey);
        }
    };

    private gotoSearch = (): void => {
        window.location.hash = '';
    };

    /**
     * Hand off to the legacy AngularJS detail page for the change-password
     * sub-flow.  Session 4 ports this natively (modal with type / autogen /
     * random options).
     */
    private changePasswordHandoff = (): void => {
        if (!this.detailUserKey) {
            return;
        }
        const url = new URL(window.location.href);
        url.searchParams.delete('modernUi');
        url.hash = `#/details/${encodeURIComponent(this.detailUserKey)}`;
        window.location.assign(url.toString());
    };

    /**
     * Hand off to the legacy verification dialog flow.  Session 4 ports this
     * natively (multi-step ATTRIBUTES / TOKEN / OTP dialog).
     */
    private verifyHandoff = (): void => {
        // Same redirect target as changePassword; the legacy detail page will
        // surface the Verify button which opens its own dialog.
        this.changePasswordHandoff();
    };

    private displayLabel(): string {
        return this.personCard?.displayNames?.[0] ?? this.detailUserKey ?? 'this user';
    }

    /**
     * Lightweight confirmation flow for the simple actions (unlock, clear,
     * delete, custom).  Session 5 polishes this with a Shoelace dialog; for
     * now native confirm() is sufficient and matches PWM's existing
     * action-confirmation tone.
     */
    private async confirmAction(
        prompt: string,
        run: () => Promise<{ successMessage?: string }>,
        afterSuccess?: () => void,
    ): Promise<void> {
        if (!this.detailUserKey) {
            return;
        }
        if (!window.confirm(prompt)) {
            return;
        }
        this.actionMessage = '';
        this.detailError = '';
        try {
            const result = await run();
            this.actionMessage = result?.successMessage ?? 'Done.';
            // Refresh the detail blob so visibleButtons / enabledButtons etc.
            // reflect the post-action state (eg. Unlock disables itself once
            // the lockout flag clears).
            if (afterSuccess) {
                afterSuccess();
            } else {
                this.loadDetail(this.detailUserKey);
            }
        } catch (err) {
            this.detailError = (err as Error).message;
        }
    }

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

    // ============================================================================
    // DETAIL FETCH
    // ============================================================================

    private async loadDetail(userKey: string): Promise<void> {
        if (this.currentDetailFetch) {
            this.currentDetailFetch.abort();
        }
        const controller = new AbortController();
        this.currentDetailFetch = controller;
        this.detailLoading = true;
        this.detailError = '';
        try {
            const [card, detail] = await Promise.all([
                getPersonCard(userKey, controller.signal),
                getPerson(userKey, controller.signal),
            ]);
            if (controller.signal.aborted) {
                return;
            }
            this.personCard = card;
            this.personDetail = detail;
            this.detailTab = 'profile';
        } catch (err) {
            if ((err as { name?: string }).name === 'AbortError') {
                return;
            }
            this.detailError = (err as Error).message;
        } finally {
            if (this.currentDetailFetch === controller) {
                this.currentDetailFetch = null;
            }
            this.detailLoading = false;
        }
    }
}

/**
 * Best-effort timestamp formatting that matches the legacy
 * {@code dateFilter}.  PWM serializes timestamps as ISO-8601 strings; this
 * renders them as the user's locale string with seconds suppressed.  If
 * parsing fails we return the raw value unchanged.
 */
function formatTimestamp(raw: string): string {
    if (!raw) {
        return '';
    }
    const date = new Date(raw);
    if (Number.isNaN(date.getTime())) {
        return raw;
    }
    return date.toLocaleString();
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

function cssUrl(value: string): string {
    return `'${value.replace(/'/g, "\\'")}'`;
}
