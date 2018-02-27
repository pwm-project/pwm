/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import { bootstrap, module } from 'angular';
import helpDeskModule from './helpdesk.module';
import routes from './routes';
import uiRouter from '@uirouter/angularjs';
import PeopleService from '../services/people.service.dev';
import HelpDeskConfigService from '../services/helpdesk-config.service.dev';
import HelpDeskService from '../services/helpdesk.service.dev';
import PasswordService from '../services/password.service.dev';
import PwmService from '../services/pwm.service.dev';


module('app', [
    uiRouter,
    helpDeskModule,
    'pascalprecht.translate',
    'ng-ias'
])
    .config(['$translateProvider', ($translateProvider: angular.translate.ITranslateProvider) => {
        $translateProvider.translations('en', require('i18n/translations_en.json'));
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
