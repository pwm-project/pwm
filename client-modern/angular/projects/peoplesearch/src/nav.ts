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
 * Cross-component navigation for peoplesearch (issue #729).  Cards, the
 * person-details modal, and the org chart all dispatch a single bubbling
 * {@code ps-navigate} event that the host {@code <pwm-peoplesearch>} listens for
 * and translates into a URL-hash change - keeping all routing logic in one
 * place instead of every sub-component touching {@code window.location}.
 */
export interface PsNavigateDetail {
    /** Open the person-details modal for this user (over the search view). */
    kind: 'details' | 'orgchart' | 'search';
    /** User key for {@code details} / {@code orgchart}. */
    key?: string;
    /** Free-text query for {@code search}. */
    query?: string;
}

export const PS_NAVIGATE = 'ps-navigate';

/** Dispatch a bubbling, composed navigation request from {@code source}. */
export function navigate(source: EventTarget, detail: PsNavigateDetail): void {
    source.dispatchEvent(
        new CustomEvent<PsNavigateDetail>(PS_NAVIGATE, { detail, bubbles: true, composed: true }),
    );
}
