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
import '@microfocus/ng-ias/dist/ng-ias';

import { bootstrap, module } from 'angular';
import helpDeskModule from './helpdesk.module';
import routes from './routes';
import uiRouter from '@uirouter/angularjs';
import PeopleService from '../../services/people.service.dev';
import HelpDeskConfigService from '../../services/helpdesk-config.service.dev';
import HelpDeskService from '../../services/helpdesk.service.dev';
import PasswordService from '../../services/password.service.dev';
import PwmService from '../../services/pwm.service.dev';


module('app', [
    uiRouter,
    helpDeskModule,
    'pascalprecht.translate',
    'ng-ias'
])
    .config(['$translateProvider', ($translateProvider: angular.translate.ITranslateProvider) => {
        $translateProvider.translations('en', require('../../i18n/translations_en.json'));
        $translateProvider.useSanitizeValueStrategy('escapeParameters');
        $translateProvider.preferredLanguage('en');
    }])
    .config(routes)
    .service('HelpDeskService', HelpDeskService)
    .service('PasswordService', PasswordService)
    .service('PeopleService', PeopleService)
    .service('PwmService', PwmService)
    .service('ConfigService', HelpDeskConfigService);

// Attach to the page document
bootstrap(document, ['app']);
