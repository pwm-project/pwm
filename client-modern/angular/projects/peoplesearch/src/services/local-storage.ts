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
 * Vanilla replacement for the AngularJS {@code LocalStorageService} from
 * {@code client/angular/src/services/local-storage.service.ts}.  Same prefix
 * ({@code PWM_}), same key names, same sessionStorage backing - so values
 * written by the legacy helpdesk (which may still be loaded on other pages
 * during the migration) round-trip into this module unchanged.  Issue #729.
 */

const PWM_PREFIX = 'PWM_';

export const StorageKeys = {
    HELPDESK_SEARCH_TEXT: 'helpdeskSearchText',
    HELPDESK_SEARCH_VIEW: 'helpdeskSearchView',
    SEARCH_TEXT: 'searchText',
    SEARCH_VIEW: 'searchView',
    VERIFICATION_STATE: 'verificationState',
} as const;

export type StorageKey = (typeof StorageKeys)[keyof typeof StorageKeys];

function storageAvailable(): boolean {
    try {
        return typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined';
    } catch {
        return false;
    }
}

export function getItem(key: StorageKey): string | null {
    if (!storageAvailable()) {
        return null;
    }
    return window.sessionStorage.getItem(PWM_PREFIX + key);
}

export function setItem(key: StorageKey, value: string | null | undefined): void {
    if (!storageAvailable()) {
        return;
    }
    if (value == null || value === '') {
        // Match legacy behavior: an empty/null assignment becomes a no-op (the
        // AngularJS impl wrote only truthy values).  removeItem to keep the
        // store clean across page reloads.
        window.sessionStorage.removeItem(PWM_PREFIX + key);
        return;
    }
    window.sessionStorage.setItem(PWM_PREFIX + key, value);
}

export function removeItem(key: StorageKey): void {
    if (!storageAvailable()) {
        return;
    }
    window.sessionStorage.removeItem(PWM_PREFIX + key);
}
