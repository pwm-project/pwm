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

/**
 * Vanilla replacement for the AngularJS {@code PwmService} from
 * {@code client/angular/src/services/pwm.service.ts}.  Wraps the PWM-style
 * {@code ?processAction=&pwmFormID=} call convention with a typed fetch helper.
 *
 * <p>The legacy implementation used $http + Angular promises; this version uses
 * native fetch + Promises and the AbortController API for cancelable searches.
 * The wire contract is unchanged: POST {@code application/json}, response shape
 * {@code { error: bool, errorCode: int, data?: T, errorMessage?: string,
 * successMessage?: string }}.  Issue #729.</p>
 */

/** Minimal subset of the legacy {@code window.PWM_GLOBAL} we depend on. */
interface PwmGlobal {
    pwmFormID?: string;
    'client.ajaxTypingWait'?: number;
    'url-context'?: string;
}

/** Common JSON envelope every PWM REST endpoint returns. */
export interface PwmEnvelope<T = unknown> {
    error: boolean;
    errorCode?: number;
    errorMessage?: string;
    successMessage?: string;
    data?: T;
}

export class PwmRequestError extends Error {
    constructor(public readonly errorCode: number | undefined, message: string) {
        super(message);
        this.name = 'PwmRequestError';
    }
}

/**
 * Build a PWM endpoint URL of the form
 * {@code <current-pathname>?processAction=<action>[&extra=...]}.  Mirrors
 * {@code PwmService.getServerUrl()} from the legacy bundle.
 */
export function getServerUrl(processAction: string, extra: Record<string, string> = {}): string {
    const params = new URLSearchParams();
    params.set('processAction', processAction);
    for (const [k, v] of Object.entries(extra)) {
        params.set(k, v);
    }
    return `${window.location.pathname}?${params.toString()}`;
}

/**
 * POST to a PWM endpoint and unwrap the JSON envelope.  Throws
 * {@link PwmRequestError} when the server returns {@code error: true} so callers
 * can write linear async/await code without checking the envelope at every site.
 *
 * <p>Optional {@code signal} hooks into a caller-managed AbortController so
 * superseded searches (eg. user typed three characters in quick succession) can
 * be cancelled in flight - the legacy {@code promiseService.abort} pattern.</p>
 */
export async function pwmFetch<T = unknown>(
    url: string,
    body: unknown,
    options: { signal?: AbortSignal } = {},
): Promise<T> {
    const global = (window as unknown as { PWM_GLOBAL?: PwmGlobal }).PWM_GLOBAL ?? {};
    const formId = global.pwmFormID ?? '';
    const urlWithFormId = url + (url.includes('?') ? '&' : '?') + 'pwmFormID=' + encodeURIComponent(formId);

    const response = await fetch(urlWithFormId, {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
        },
        body: body == null ? null : JSON.stringify(body),
        signal: options.signal,
        credentials: 'same-origin',
    });

    if (!response.ok) {
        throw new PwmRequestError(response.status, `HTTP ${response.status} ${response.statusText}`);
    }

    const envelope = (await response.json()) as PwmEnvelope<T>;
    if (envelope.error) {
        throw new PwmRequestError(envelope.errorCode, envelope.errorMessage ?? 'unknown PWM error');
    }
    // Some endpoints return data, some return only successMessage at the top
    // level; mirror the legacy "return response.data" passthrough.
    return (envelope.data ?? (envelope as unknown as T));
}

/** Debounce duration (ms) the server-configured ajax typing wait, with fallback. */
export function ajaxTypingWait(): number {
    const global = (window as unknown as { PWM_GLOBAL?: PwmGlobal }).PWM_GLOBAL;
    return global?.['client.ajaxTypingWait'] ?? 700;
}
