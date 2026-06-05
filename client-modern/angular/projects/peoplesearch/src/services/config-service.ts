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

import { getServerUrl, PwmRequestError } from './pwm-fetch';

/**
 * Replacement for {@code PeopleSearchConfigService} + {@code ConfigBaseService}
 * from the legacy bundle (issue #729).  The PWM backend exposes a single
 * {@code processAction=clientData} endpoint returning a JSON object keyed by
 * config name; we cache the whole response once per page load and resolve from
 * the cached map.
 */

export interface AttributeMetadata {
    attribute: string;
    label: string;
    type: string;
    options?: Record<string, string>;
}

export interface AdvancedSearchConfig {
    enabled: boolean;
    maxRows: number;
    attributes: AttributeMetadata[];
}

export interface SearchColumns {
    [attributeName: string]: string;
}

/** Aggregated config the person-details modal needs for its action buttons. */
export interface PersonDetailsConfig {
    photosEnabled: boolean;
    orgChartEnabled: boolean;
    exportEnabled: boolean;
    emailTeamEnabled: boolean;
    maxExportDepth: number;
    maxEmailDepth: number;
}

let cached: Promise<Record<string, unknown>> | null = null;

async function loadClientData(signal?: AbortSignal): Promise<Record<string, unknown>> {
    if (cached) {
        return cached;
    }
    cached = (async (): Promise<Record<string, unknown>> => {
        const url = getServerUrl('clientData');
        const response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
            credentials: 'same-origin',
            signal,
        });
        if (!response.ok) {
            throw new PwmRequestError(response.status, `HTTP ${response.status} ${response.statusText}`);
        }
        const envelope = (await response.json()) as { error?: boolean; errorCode?: number; errorMessage?: string; data?: Record<string, unknown> };
        if (envelope.error) {
            throw new PwmRequestError(envelope.errorCode, envelope.errorMessage ?? 'unknown PWM error');
        }
        return envelope.data ?? {};
    })();
    return cached;
}

async function getValue<T>(key: string, signal?: AbortSignal): Promise<T | undefined> {
    const data = await loadClientData(signal);
    return data[key] as T | undefined;
}

export async function advancedSearchConfig(signal?: AbortSignal): Promise<AdvancedSearchConfig> {
    const [enabled, maxRows, attributes] = await Promise.all([
        getValue<boolean>('enableAdvancedSearch', signal),
        getValue<number>('maxAdvancedSearchAttributes', signal),
        getValue<AttributeMetadata[]>('advancedSearchAttributes', signal),
    ]);
    return {
        enabled: Boolean(enabled),
        maxRows: typeof maxRows === 'number' && maxRows > 0 ? maxRows : 4,
        attributes: Array.isArray(attributes) ? attributes : [],
    };
}

export async function searchColumns(signal?: AbortSignal): Promise<SearchColumns> {
    const cols = await getValue<SearchColumns>('searchColumns', signal);
    return cols ?? {};
}

export async function photosEnabled(signal?: AbortSignal): Promise<boolean> {
    // Legacy default-on-error was true.
    return (await getValue<boolean>('enablePhoto', signal)) !== false;
}

export async function orgChartEnabled(signal?: AbortSignal): Promise<boolean> {
    // Legacy default-on-error was true.
    return (await getValue<boolean>('orgChartEnabled', signal)) !== false;
}

export async function orgChartShowChildCount(signal?: AbortSignal): Promise<boolean> {
    return Boolean(await getValue<boolean>('orgChartShowChildCount', signal));
}

export async function getOrgChartMaxParents(signal?: AbortSignal): Promise<number> {
    const value = await getValue<number>('orgChartMaxParents', signal);
    return typeof value === 'number' && value > 0 ? value : 10;
}

export async function printingEnabled(signal?: AbortSignal): Promise<boolean> {
    return Boolean(await getValue<boolean>('enableOrgChartPrinting', signal));
}

export async function personDetailsConfig(signal?: AbortSignal): Promise<PersonDetailsConfig> {
    const [photos, orgChart, exportEnabled, maxExportDepth, mailto, maxEmailDepth] = await Promise.all([
        getValue<boolean>('enablePhoto', signal),
        getValue<boolean>('orgChartEnabled', signal),
        getValue<boolean>('enableExport', signal),
        getValue<number>('exportMaxDepth', signal),
        getValue<boolean>('enableMailtoLinks', signal),
        getValue<number>('mailtoLinkMaxDepth', signal),
    ]);
    return {
        photosEnabled: photos !== false,
        orgChartEnabled: orgChart !== false,
        exportEnabled: Boolean(exportEnabled),
        emailTeamEnabled: Boolean(mailto),
        maxExportDepth: typeof maxExportDepth === 'number' && maxExportDepth > 0 ? maxExportDepth : 1,
        maxEmailDepth: typeof maxEmailDepth === 'number' && maxEmailDepth > 0 ? maxEmailDepth : 1,
    };
}

/** Default value for an advanced-search attribute (first option for selects). */
export function defaultValueForAttribute(meta: AttributeMetadata): string {
    if (meta.type === 'select' && meta.options) {
        return Object.keys(meta.options)[0] ?? '';
    }
    return '';
}
