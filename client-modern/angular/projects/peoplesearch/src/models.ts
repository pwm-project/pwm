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
 * Domain types shared by the peoplesearch Lit components.  Mirrors the legacy
 * AngularJS {@code IPerson} / {@code SearchResult} / {@code IOrgChartData}
 * interfaces so the JSON wire format from PWM's backend deserializes as-is.
 * Issue #729.
 */

/** A single detail attribute row on the person-details modal. */
export interface PersonDetailAttribute {
    label: string;
    /** {@code userDN}, {@code email}, {@code tel}, or a plain type. */
    type: string;
    values?: string[];
    /** Present when {@code type === 'userDN'} - links to other people. */
    userReferences?: Array<{ userKey: string; displayName: string }>;
    /** When true, render an inline "search for this value" affordance. */
    searchable?: boolean;
}

export interface PersonLink {
    name: string;
    link: string;
}

export interface Person {
    userKey?: string;
    numDirectReports?: number;

    /** Single-line label used by autocomplete / simple search responses. */
    _displayName?: string;

    /** Multi-line label set used by the detail / card / org-chart responses. */
    displayNames?: string[];

    /** URL to the user's photo, or undefined if photos are disabled. */
    photoURL?: string;

    /** Keyed attribute map on the detail response (key -> attribute). */
    detail?: Record<string, PersonDetailAttribute>;
    links?: PersonLink[];

    givenName?: string;
    sn?: string;
    mail?: string;
    telephoneNumber?: string;
    title?: string;
}

export interface SearchResult {
    sizeExceeded: boolean;
    people: Person[];
}

/** Wire shape PWM's backend uses for {@code processAction=search} responses. */
export interface SearchResultRaw {
    sizeExceeded?: boolean;
    searchResults?: Person[];
}

/** Normalized {@code orgChartData} response. */
export interface OrgChartData {
    self: Person;
    manager?: Person;
    children: Person[];
    assistant?: Person;
}

/** Wire shape of the {@code orgChartData} response before normalization. */
export interface OrgChartDataRaw {
    self: Person;
    parent?: Person;
    children?: Person[];
    assistant?: Person;
}
