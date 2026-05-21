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
 * Domain types shared by the helpdesk Lit components.  Mirrors the legacy
 * AngularJS {@code IPerson} / {@code SearchResult} interfaces so the JSON wire
 * format from PWM's backend continues to deserialize as-is.  Issue #729.
 */

export interface Person {
    userKey?: string;
    numDirectReports?: number;

    /** Single-line label used by autocomplete responses (search variant). */
    _displayName?: string;

    /** Multi-line label set used by the detail / card responses. */
    displayNames?: string[];

    /** URL to the user's photo, or undefined if photos are disabled. */
    photoURL?: string;

    detail?: unknown;
    links?: unknown[];

    givenName?: string;
    sn?: string;
    mail?: string;
    telephoneNumber?: string;
    title?: string;
}

export interface SearchResult {
    sizeExceeded: boolean;
    /**
     * Raw entries returned by PWM's search endpoint.  The server wraps them in
     * a {@code searchResults} field; the helpdesk service unwraps it before
     * handing the shape to UI code (matches the legacy SearchResult ctor).
     */
    people: Person[];
}

/** Wire shape PWM's backend uses for {@code processAction=search} responses. */
export interface SearchResultRaw {
    sizeExceeded?: boolean;
    searchResults?: Person[];
}
