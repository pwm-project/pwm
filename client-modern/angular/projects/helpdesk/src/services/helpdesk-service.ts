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
import type { Person, SearchResult, SearchResultRaw } from '../models';

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

/** Fetch the small card-view summary for a single user (by userKey). */
export async function getPersonCard(userKey: string, signal?: AbortSignal): Promise<Person> {
    const url = getServerUrl('card', { userKey });
    return pwmFetch<Person>(url, null, { signal });
}

function toSearchResult(raw: SearchResultRaw | undefined): SearchResult {
    const safe = raw ?? {};
    return {
        sizeExceeded: Boolean(safe.sizeExceeded),
        people: Array.isArray(safe.searchResults) ? safe.searchResults : [],
    };
}
