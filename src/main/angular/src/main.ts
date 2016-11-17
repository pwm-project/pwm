import { bootstrap, module } from 'angular';
import ConfigService from './services/config.service';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import PeopleService from './services/people.service';
import PwmService from './services/pwm.service';
import routes from './routes';
import TranslationsLoaderFactory from './services/translations-loader.factory';
import uiRouter from 'angular-ui-router';

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
            $translateProvider.useLoader('translationsLoader');
            $translateProvider.useSanitizeValueStrategy('escapeParameters');
            $translateProvider.preferredLanguage('en');
        }])
    .service('PeopleService', PeopleService)
    .service('PwmService', PwmService)
    .service('ConfigService', ConfigService)
    .factory('translationsLoader', TranslationsLoaderFactory);

// Attach to the page document
bootstrap(document, ['app']);

