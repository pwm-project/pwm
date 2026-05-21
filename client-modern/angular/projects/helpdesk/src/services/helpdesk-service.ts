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

import { getServerUrl, pwmFetch } from './pwm-fetch';
import type { SearchResult, SearchResultRaw } from '../models';

/**
 * Advanced-search query row.  Mirrors {@code IAdvancedSearchQuery} from the
 * legacy {@code base-config.service.ts}.
 */
export interface AdvancedSearchQuery {
    key: string;
    value: string;
}

/**
 * Subset of {@code client/angular/src/services/helpdesk.service.ts} ported to
 * vanilla TS for the session 1 cards-view slice (issue #729).  Methods will be
 * added in subsequent sessions as additional helpdesk surfaces migrate; this
 * file is deliberately small to keep the initial diff reviewable.
 *
 * <p>The wire contract is unchanged - same {@code processAction} names, same
 * JSON envelope shape, same {@code mode: 'simple'} / {@code mode: 'advanced'}
 * dispatch.  The legacy SearchResult constructor's
 * {@code options.searchResults.map(p =&gt; p)} pass-through is folded into the
 * adapter below.</p>
 */

/** Search by free-text query (the simple-mode helpdesk search). */
export async function search(query: string, signal?: AbortSignal): Promise<SearchResult> {
    const url = getServerUrl('search');
    const raw = await pwmFetch<SearchResultRaw>(
        url,
        { mode: 'simple', username: query },
        { signal },
    );
    return toSearchResult(raw);
}

/**
 * Multi-attribute (advanced) search.  Each query row contributes a single
 * attribute=value pair the backend ANDs together.  Mirrors
 * {@code HelpDeskService.advancedSearch} from the legacy bundle.
 */
export async function advancedSearch(queries: AdvancedSearchQuery[], signal?: AbortSignal): Promise<SearchResult> {
    const url = getServerUrl('search');
    const raw = await pwmFetch<SearchResultRaw>(
        url,
        { mode: 'advanced', searchValues: queries },
        { signal },
    );
    return toSearchResult(raw);
}

function toSearchResult(raw: SearchResultRaw | undefined): SearchResult {
    const safe = raw ?? {};
    return {
        sizeExceeded: Boolean(safe.sizeExceeded),
        people: Array.isArray(safe.searchResults) ? safe.searchResults : [],
    };
}

// ----------------------------------------------------------------------------
// Detail page (session 3)
// ----------------------------------------------------------------------------

/**
 * Single attribute row as returned by the detail endpoint.  PWM types these
 * server-side as one of {@code string}, {@code number}, {@code timestamp},
 * {@code multiString}.  The {@code value} field is only populated for the
 * scalar types; {@code multiString} uses {@code values}.
 */
export interface DetailAttribute {
    label: string;
    type: 'string' | 'number' | 'timestamp' | 'multiString' | string;
    value?: string;
    values?: string[];
}

/** History entry under {@code person.userHistory}. */
export interface HistoryEntry {
    label: string;
    timestamp: string;
}

/**
 * Full detail blob the {@code processAction=detail} endpoint returns.  Field
 * presence is conditional - status / history / password rules / responses are
 * only present when the configured profile populates them.  Mirrors
 * {@code helpdesk-detail.component.ts}'s use of {@code this.person}.
 */
export interface PersonDetail {
    userKey?: string;
    displayName?: string;
    profileData?: DetailAttribute[];
    statusData?: DetailAttribute[];
    userHistory?: HistoryEntry[];
    passwordPolicyDN?: string;
    passwordPolicyID?: string;
    passwordRequirements?: string[];
    passwordPolicyRules?: Record<string, string>;
    helpdeskResponses?: DetailAttribute[];
    visibleButtons?: string[];
    enabledButtons?: string[];
}

/** Card-info envelope from the {@code card} endpoint. */
export interface PersonCard {
    userKey?: string;
    displayNames?: string[];
    photoURL?: string;
}

/** Generic success envelope - some endpoints surface only {@code successMessage}. */
export interface SuccessResponse {
    successMessage?: string;
}

/** Full detail payload for a single user. */
export async function getPerson(userKey: string, signal?: AbortSignal): Promise<PersonDetail> {
    const url = getServerUrl('detail', { userKey });
    return pwmFetch<PersonDetail>(url, null, { signal });
}

/** Card-summary payload (name + photo URL) for a single user. */
export async function getPersonCard(userKey: string, signal?: AbortSignal): Promise<PersonCard> {
    const url = getServerUrl('card', { userKey });
    return pwmFetch<PersonCard>(url, null, { signal });
}

/** Clear the user's intruder lockout (resets the bad-attempt counter). */
export async function unlockIntruder(userKey: string, signal?: AbortSignal): Promise<SuccessResponse> {
    const url = getServerUrl('unlockIntruder', { userKey });
    return pwmFetch<SuccessResponse>(url, null, { signal });
}

/** Clear the user's saved OTP secret so they can re-enroll. */
export async function clearOtpSecret(userKey: string, signal?: AbortSignal): Promise<SuccessResponse> {
    const url = getServerUrl('clearOtpSecret');
    return pwmFetch<SuccessResponse>(url, { userKey }, { signal });
}

/** Clear the user's saved challenge / response answers. */
export async function clearResponses(userKey: string, signal?: AbortSignal): Promise<SuccessResponse> {
    const url = getServerUrl('clearResponses', { userKey });
    return pwmFetch<SuccessResponse>(url, null, { signal });
}

/** Delete the user account.  This is a destructive call; callers must confirm first. */
export async function deleteUser(userKey: string, signal?: AbortSignal): Promise<SuccessResponse> {
    const url = getServerUrl('deleteUser', { userKey });
    return pwmFetch<SuccessResponse>(url, null, { signal });
}

/**
 * Invoke an admin-configured custom action (configured via PWM's helpdesk
 * profile -&gt; Custom Actions).  Mirrors the legacy
 * {@code HelpDeskService.customAction}.
 */
export async function customAction(actionName: string, userKey: string, signal?: AbortSignal): Promise<SuccessResponse> {
    const url = getServerUrl('executeAction', { name: actionName });
    return pwmFetch<SuccessResponse>(url, { userKey }, { signal });
}
