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

import 'angular';
import 'angular-translate';
import 'angular-sanitize';
import '@microfocus/ng-ias/dist/ng-ias';

// Add a polyfill for Set() for IE11, since it's used in peoplesearch-base.component.ts
import 'core-js/es/set';

import { bootstrap, module } from 'angular';
import helpDeskModule from './helpdesk.module';
import PeopleService from '../../services/people.service';
import PwmService from '../../services/pwm.service';
import routes from './routes';
import TranslationsLoaderFactory from '../../services/translations-loader.factory';
import uiRouter from '@uirouter/angularjs';
import HelpDeskConfigService from '../../services/helpdesk-config.service';
import HelpDeskService from '../../services/helpdesk.service';
import PasswordService from '../../services/password.service';


module('app', [
    uiRouter,
    helpDeskModule,
    'pascalprecht.translate',
    'ng-ias'
])
    .config(routes)
    .config([
        '$translateProvider',
        ($translateProvider: angular.translate.ITranslateProvider) => {
            $translateProvider
                .translations('fallback', require('../../i18n/translations_en.json'))
                .useLoader('translationsLoader')
                .useSanitizeValueStrategy('escapeParameters')
                .preferredLanguage('en')
                .fallbackLanguage('fallback')
                .forceAsyncReload(true);
        }])
    .service('HelpDeskService', HelpDeskService)
    .service('PasswordService', PasswordService)
    .service('PeopleService', PeopleService)
    .service('PwmService', PwmService)
    .service('ConfigService', HelpDeskConfigService)
    .factory('translationsLoader', TranslationsLoaderFactory);

// Attach to the page document, wait for PWM to load first
window['PWM_GLOBAL'].startupFunctions.push(() => {
    bootstrap(document, ['app'], { strictDi: true });
});
