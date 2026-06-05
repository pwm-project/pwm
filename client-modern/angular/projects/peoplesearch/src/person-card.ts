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

import { html, nothing, type TemplateResult } from 'lit';
import type { Person } from './models';

export interface PersonCardOptions {
    photosEnabled: boolean;
    /** How many secondary display-name lines to show beneath the headline. */
    secondaryLines: number;
    onClick: (person: Person) => void;
    /** Render the direct-report count badge (org chart). */
    showReports?: boolean;
    /** Visual size class suffix. */
    size?: 'normal' | 'small' | 'large';
}

function cssUrl(value: string): string {
    return `'${value.replace(/'/g, "\\'")}'`;
}

/**
 * Shared person tile used by the search cards view and the org chart.  Mirrors
 * the legacy {@code personCard} directive: avatar (if photos enabled), a
 * headline display name, N secondary lines sliced from {@code displayNames},
 * and an optional direct-report count badge.  Keyboard accessible (Enter /
 * Space activate).
 */
export function renderPersonCard(person: Person, opts: PersonCardOptions): TemplateResult {
    const names = person.displayNames ?? (person._displayName ? [person._displayName] : []);
    const head = names[0] ?? person.userKey ?? '';
    const rest = names.slice(1, 1 + Math.max(0, opts.secondaryLines));
    const avatarStyle = opts.photosEnabled && person.photoURL
        ? `background-image:url(${cssUrl(person.photoURL)})`
        : '';
    const activate = (): void => opts.onClick(person);
    return html`
        <div
            class="pwm-ps-card"
            data-size=${opts.size ?? 'normal'}
            role="listitem"
            tabindex="0"
            @click=${activate}
            @keydown=${(e: KeyboardEvent) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    activate();
                }
            }}
        >
            ${opts.photosEnabled
                ? html`<div class="pwm-ps-card-avatar" style=${avatarStyle} aria-hidden="true"></div>`
                : nothing}
            <div class="pwm-ps-card-body">
                <h3 class="pwm-ps-card-name" title=${head}>${head}</h3>
                ${rest.map((line) => html`<div class="pwm-ps-card-line" title=${line}>${line}</div>`)}
                ${opts.showReports && person.numDirectReports
                    ? html`<div class="pwm-ps-card-reports">${person.numDirectReports} reports</div>`
                    : nothing}
            </div>
        </div>
    `;
}
