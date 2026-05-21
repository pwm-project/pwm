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

// Entry point for the helpdesk client-modern bundle (issue #729).  The
// <pwm-helpdesk> custom element registers itself on import (decorator side-
// effect at the bottom of helpdesk.element.ts).
import './helpdesk.element';

// Build-time version marker so we can verify in DevTools which bundle the
// browser actually ran (cached vs fresh).  Bump this whenever a session
// changes behavior callers might still expect from an earlier version.
// eslint-disable-next-line no-console
console.log('[pwm-helpdesk] bundle session 3 (detail page + hash routing)');
