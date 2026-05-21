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
 * Replacement for {@code HelpDeskConfigService} + {@code ConfigBaseService} from
 * the legacy bundle (issue #729).  The PWM backend exposes a single
 * {@code processAction=clientData} endpoint that returns a JSON object whose
 * top-level keys are the config values the UI needs.  The legacy services
 * called {@code GET ?processAction=clientData} fresh for each key (with
 * angular {@code $http} caching the response).  Here we cache the entire
 * response once per page load and resolve from the cached map - same effective
 * behavior, fewer in-flight requests, and no Angular-specific cache plumbing.
 */

export interface AttributeMetadata {
    attribute: string;
    label: string;
    type: string;
    /** Used when {@code type === 'select'}: a {@code Record<value, label>} map. */
    options?: Record<string, string>;
}

export interface AdvancedSearchConfig {
    enabled: boolean;
    maxRows: number;
    attributes: AttributeMetadata[];
}

export interface SearchColumns {
    /** Map of attribute name -&gt; localized column label, ordered. */
    [attributeName: string]: string;
}

/** Cached {@code clientData} response for the lifetime of this page load. */
let cached: Promise<Record<string, unknown>> | null = null;

async function loadClientData(signal?: AbortSignal): Promise<Record<string, unknown>> {
    if (cached) {
        return cached;
    }
    cached = (async (): Promise<Record<string, unknown>> => {
        const url = getServerUrl('clientData');
        // clientData is a GET in the legacy bundle.  pwmFetch currently always
        // POSTs - issue a GET via fetch directly to match the wire contract.
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

/** Fetch a single key from the {@code clientData} response. */
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
    const value = await getValue<boolean>('enablePhoto', signal);
    // Legacy default-on-error was true - keep that behavior so a stricter
    // backend that omits the flag still shows photos.
    return value !== false;
}

/** Admin-configured "Custom Actions" available on the detail page action strip. */
export interface CustomActionButton {
    /** Identifier used by the executeAction endpoint as {@code name=} parameter. */
    name: string;
    /** Tooltip shown on hover. */
    description?: string;
}

/**
 * Custom action buttons configured on the helpdesk profile.  PWM returns these
 * as a map keyed by an arbitrary label, with each value carrying the
 * {@code name} the {@code executeAction} endpoint expects.
 */
export async function customActionButtons(signal?: AbortSignal): Promise<Record<string, CustomActionButton>> {
    const value = await getValue<Record<string, CustomActionButton>>('actions', signal);
    return value ?? {};
}

/**
 * Compute the default value for an advanced-search attribute based on its
 * type.  For {@code type === 'select'} the default is the first option's key;
 * for everything else it is the empty string.  Mirrors
 * {@code CommonSearchService.getDefaultValue} in the legacy bundle.
 */
export function defaultValueForAttribute(meta: AttributeMetadata): string {
    if (meta.type === 'select' && meta.options) {
        const firstKey = Object.keys(meta.options)[0];
        return firstKey ?? '';
    }
    return '';
}
