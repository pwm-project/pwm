/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import ConfigService from './services/config.service.dev';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import PeopleService from './services/people.service.dev';
import PwmService from './services/pwm.service.dev';
import routes from './routes';
import routeErrorHandler from './route-error-handler';
import uiRouter from 'angular-ui-router';

// fontgen-loader needs this :(
require('./icons.json');

module('app', [
    uiRouter,
    peopleSearchModule,
    'pascalprecht.translate'
])

    .config(routes)
    .config(['$translateProvider', ($translateProvider: angular.translate.ITranslateProvider) => {
        $translateProvider.translations('en', require('i18n/translations_en.json'));
        $translateProvider.useSanitizeValueStrategy('escapeParameters');
        $translateProvider.preferredLanguage('en');
    }])
    .run(routeErrorHandler)
    .service('PeopleService', PeopleService)
    .service('PwmService', PwmService)
    .service('ConfigService', ConfigService);

// Attach to the page document
bootstrap(document, ['app']);
