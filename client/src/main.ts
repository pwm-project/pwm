/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import ConfigService from './services/peoplesearch-config.service';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import PeopleService from './services/people.service';
import PwmService from './services/pwm.service';
import routes from './routes';
import routeErrorHandler from './route-error-handler';
import TranslationsLoaderFactory from './services/translations-loader.factory';
import uiRouter from '@uirouter/angularjs';

// fontgen-loader needs this :(
require('./icons.json');

module('app', [
    uiRouter,
    peopleSearchModule,
    'pascalprecht.translate'
])

    .config(routes)
    .config([
        '$translateProvider',
        ($translateProvider: angular.translate.ITranslateProvider) => {
            $translateProvider
                .translations('fallback', require('i18n/translations_en.json'))
                .useLoader('translationsLoader')
                .useSanitizeValueStrategy('escapeParameters')
                .preferredLanguage('en')
                .fallbackLanguage('fallback')
                .forceAsyncReload(true);
        }])
    .config([
        '$locationProvider', ($locationProvider: angular.ILocationProvider) => {
        $locationProvider.hashPrefix('');
    }])
    .run(routeErrorHandler)
    .service('PeopleService', PeopleService)
    .service('PwmService', PwmService)
    .service('ConfigService', ConfigService)
    .factory('translationsLoader', TranslationsLoaderFactory);

// Attach to the page document, wait for PWM to load first
window['PWM_GLOBAL'].startupFunctions.push(() => {
    bootstrap(document, ['app'], { strictDi: true });
});

