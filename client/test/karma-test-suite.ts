/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

Error.stackTraceLimit = Infinity;

import 'angular';
import 'angular-mocks';

// This creates a single bundle with all test cases (*.test.ts), which improves performance
// (i.e. we don't create a webpack bundle for each test)

// To run all tests, use this:
// var appContext = (require as any).context('../src', true, /\.test\.ts/);

// To run a specific test, change the following regular expression and use this:
var appContext = (require as any).context('../src', true, /common-search.service.test.ts/);

appContext.keys().forEach(appContext);
