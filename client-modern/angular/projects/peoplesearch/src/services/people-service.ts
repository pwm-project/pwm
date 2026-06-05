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

import { getServerUrl, pwmFetch, pwmFetchGet } from './pwm-fetch';
import type {
    OrgChartData,
    OrgChartDataRaw,
    Person,
    SearchResult,
    SearchResultRaw,
} from '../models';

/** Advanced-search query row (attribute=value pair the backend ANDs together). */
export interface AdvancedSearchQuery {
    key: string;
    value: string;
}

/**
 * Vanilla port of the legacy {@code PeopleService} (issue #729).  Same
 * {@code processAction} names and JSON envelope shapes; search is a POST,
 * the detail / org-chart / mailto endpoints are GETs (matching the legacy
 * verbs).
 */

function toSearchResult(raw: SearchResultRaw | undefined): SearchResult {
    const safe = raw ?? {};
    return {
        sizeExceeded: Boolean(safe.sizeExceeded),
        people: Array.isArray(safe.searchResults) ? safe.searchResults : [],
    };
}

/** Free-text (simple) search. */
export async function search(query: string, signal?: AbortSignal): Promise<SearchResult> {
    const url = getServerUrl('search');
    const raw = await pwmFetch<SearchResultRaw>(url, { mode: 'simple', username: query }, { signal });
    return toSearchResult(raw);
}

/** Multi-attribute (advanced) search. */
export async function advancedSearch(queries: AdvancedSearchQuery[], signal?: AbortSignal): Promise<SearchResult> {
    const url = getServerUrl('search');
    const raw = await pwmFetch<SearchResultRaw>(url, { mode: 'advanced', searchValues: queries }, { signal });
    return toSearchResult(raw);
}

/**
 * Autocomplete used by the org-chart search box: a simple search with
 * {@code includeDisplayName}, capped at the first ten matches.
 */
export async function autoComplete(query: string, signal?: AbortSignal): Promise<Person[]> {
    const url = getServerUrl('search');
    const raw = await pwmFetch<SearchResultRaw>(
        url,
        { mode: 'simple', username: query, includeDisplayName: true },
        { signal },
    );
    const people = toSearchResult(raw).people;
    return people.length > 10 ? people.slice(0, 10) : people;
}

/** Full detail payload for a single user (person-details modal). */
export async function getPerson(userKey: string, signal?: AbortSignal): Promise<Person> {
    const url = getServerUrl('detail', { userKey });
    return pwmFetchGet<Person>(url, { signal });
}

function toOrgChartData(raw: OrgChartDataRaw): OrgChartData {
    return {
        self: raw.self,
        manager: raw.parent,
        assistant: raw.assistant,
        children: Array.isArray(raw.children) ? raw.children : [],
    };
}

/** One level of org-chart data (self + immediate parent / children / assistant). */
export async function getOrgChartData(
    userKey: string | undefined,
    noChildren: boolean,
    signal?: AbortSignal,
): Promise<OrgChartData> {
    const extra: Record<string, string> = { noChildren: String(noChildren) };
    if (userKey) {
        extra['userKey'] = userKey;
    }
    const url = getServerUrl('orgChartData', extra);
    const raw = await pwmFetchGet<OrgChartDataRaw>(url, { signal });
    return toOrgChartData(raw);
}

/** Direct reports of a user (the children of one org-chart level). */
export async function getDirectReports(userKey: string, signal?: AbortSignal): Promise<Person[]> {
    const data = await getOrgChartData(userKey, false, signal);
    return data.children;
}

/**
 * Walk up the management chain from {@code userKey}, up to {@code limit}
 * managers.  Each hop is a separate {@code orgChartData} call (matching the
 * legacy recursive traversal).
 */
export async function getManagementChain(
    userKey: string,
    limit: number,
    signal?: AbortSignal,
): Promise<Person[]> {
    const managers: Person[] = [];
    let currentKey: string | undefined = userKey;
    while (currentKey && managers.length < limit) {
        const data: OrgChartData = await getOrgChartData(currentKey, true, signal);
        if (!data.manager) {
            break;
        }
        managers.push(data.manager);
        currentKey = data.manager.userKey;
    }
    return managers;
}

/** Team email addresses for a user's org tree down to {@code depth} levels. */
export async function getTeamEmails(userKey: string, depth: number, signal?: AbortSignal): Promise<string[]> {
    const url = getServerUrl('mailtoLinks', { userKey, depth: String(depth) });
    const data = await pwmFetchGet<string[]>(url, { signal });
    return Array.isArray(data) ? data : [];
}

/**
 * Build the download URL for an org-chart export at the given depth.  The
 * legacy bundle navigated the window straight to this URL (the response is a
 * file download, not JSON).
 */
export function exportOrgChartUrl(userKey: string, depth: number): string {
    return getServerUrl('exportOrgChart', { userKey, depth: String(depth) });
}
