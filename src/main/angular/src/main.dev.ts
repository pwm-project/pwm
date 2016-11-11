import { bootstrap, module } from 'angular';
import ConfigService from './services/config.service.dev';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import PeopleService from './services/people.service.dev';
import routes from './routes';
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
    .service('PeopleService', PeopleService)
    .service('ConfigService', ConfigService);

// Attach to the page document
bootstrap(document, ['app']);
