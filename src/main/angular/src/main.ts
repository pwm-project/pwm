import { bootstrap, module } from 'angular';
import ConfigService from './services/config.service';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import PeopleService from './services/people.service';
import routes from './routes';
import translationsLoader from './services/translations-loader.factory';
import uiRouter from 'angular-ui-router';

module('app', [
    uiRouter,
    peopleSearchModule,
    'pascalprecht.translate'
])

    .config(routes)
    .config(['$translateProvider', ($translateProvider: angular.translate.ITranslateProvider) => {
        $translateProvider.useLoader('translationsLoader');
        $translateProvider.useSanitizeValueStrategy('escapeParameters');
        $translateProvider.preferredLanguage('en');
    }])
    .service('PeopleService', PeopleService)
    .service('ConfigService', ConfigService)
    .factory('translationsLoader', translationsLoader);

// Attach to the page document
bootstrap(document, ['app']);

