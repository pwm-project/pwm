/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import 'textangular';
import 'textangular/dist/textAngular-sanitize';
import * as angular from 'angular';

import { module } from 'angular';

import ConfigEditorController from './configeditor.controller';

module('configeditor.module', ['textAngular'])
    .controller('ConfigEditorController', ConfigEditorController);

// lowercase and uppercase have been removed from angular, but textAngular still hasn't caught up with the change. So
// The following polyfills it for now:

// @ts-ignore
if (!angular.lowercase) angular.lowercase = (str) => str ? str.toLowerCase() : str;
// @ts-ignore
if (!angular.uppercase) angular.uppercase = (str) => str ? str.toUpperCase() : str;
