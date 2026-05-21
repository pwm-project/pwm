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
import { search } from './services/helpdesk-service';
import { ajaxTypingWait } from './services/pwm-fetch';
import { getItem, setItem, StorageKeys } from './services/local-storage';
import type { Person, SearchResult } from './models';

/**
 * Session-1 vertical slice of the helpdesk migration (issue #729): the
 * cards-view of the search page.  Replaces the AngularJS
 * {@code helpdesk-search-cards.component} but only covers the simple-mode
 * search and the cards grid - advanced search, table view, the detail page,
 * and the verification dialogs all still live in the legacy AngularJS bundle
 * and remain reachable when the {@code ?modernUi=1} flag is absent.</p>
 *
 * <p>The component renders into its own light DOM (via {@code createRenderRoot})
 * so the existing PWM stylesheets (legacy helpdesk CSS, theme overrides) still
 * apply.  Lit handles the change detection and templating; nothing else.</p>
 */
@customElement('pwm-helpdesk')
export class HelpdeskElement extends LitElement {
    /**
     * Lit renders into the shadow DOM by default; opt out so the global PWM
     * stylesheets (theme, fonts, layout overrides) still cascade in.  All four
     * migrated components use this same pattern - it matches the JSP-as-host
     * model PWM has always used.
     */
    protected override createRenderRoot(): this {
        return this;
    }

    @state() private query: string = '';
    @state() private status: string = '';
    @state() private error: string = '';
    @state() private searchResult: SearchResult | null = null;

    private debounceTimer: ReturnType<typeof setTimeout> | null = null;
    private currentSearch: AbortController | null = null;
    private debounceMs: number = 700;

    override connectedCallback(): void {
        super.connectedCallback();
        this.debounceMs = ajaxTypingWait();
        // Restore the user's last query string from the same sessionStorage key
        // the legacy helpdesk uses, so flipping between AngularJS and modern
        // surfaces is seamless during the migration window.
        const stored = getItem(StorageKeys.HELPDESK_SEARCH_TEXT);
        if (stored) {
            this.query = stored;
            this.kickOffSearch();
        }
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        this.cancelDebounce();
        this.abortInFlight();
    }

    override render(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-header">
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
            </div>

            <div class="pwm-helpdesk-status" data-error=${this.error ? 'true' : 'false'}>
                ${this.error || this.status || nothing}
            </div>

            ${this.renderCards()}
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

    /**
     * For search responses that omit {@code displayNames}, fall back to the
     * legacy auto-complete fields so the card still has something useful in it.
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

    private onQueryInput = (event: Event): void => {
        const value = (event.target as HTMLInputElement).value;
        this.query = value;
        setItem(StorageKeys.HELPDESK_SEARCH_TEXT, value);
        this.cancelDebounce();
        this.debounceTimer = setTimeout(() => this.kickOffSearch(), this.debounceMs);
    };

    private onSelectPerson(person: Person): void {
        // Session 1 stub - the helpdesk detail page is not yet migrated.  Hand
        // the user back to the legacy AngularJS detail route so a click on a
        // card still does something useful.  Subsequent sessions replace this
        // with a <pwm-helpdesk-detail> view.
        if (person.userKey) {
            window.location.hash = `#/details/${encodeURIComponent(person.userKey)}`;
            // Strip the modernUi flag so the legacy bundle takes over for the
            // detail route the new bundle does not yet implement.
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

    private async kickOffSearch(): Promise<void> {
        this.abortInFlight();
        const query = this.query.trim();
        if (!query) {
            this.searchResult = null;
            this.status = '';
            this.error = '';
            return;
        }

        const controller = new AbortController();
        this.currentSearch = controller;
        this.error = '';
        this.status = 'Searching…';

        try {
            const result = await search(query, controller.signal);
            if (controller.signal.aborted) {
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
            if (this.currentSearch === controller) {
                this.currentSearch = null;
            }
        }
    }
}

/**
 * Conservative CSS url() escaping for an attribute value we are interpolating
 * into a style string.  PWM's photoURL is a backend-supplied URL, but the
 * defensive escape matches the legacy avatarStyle helper's intent.
 */
function cssUrl(value: string): string {
    return `'${value.replace(/'/g, "\\'")}'`;
}
