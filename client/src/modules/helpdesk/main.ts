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

import 'angular';
import 'angular-translate';
import '@microfocus/ng-ias/dist/ng-ias';

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
