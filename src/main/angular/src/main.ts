import { bootstrap, module } from 'angular';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import PeopleService from './services/people.service';
import routes from './routes';
import translations from './translations';
import uiRouter from 'angular-ui-router';

module('app', [
    uiRouter,
    peopleSearchModule,
    'pascalprecht.translate'
])

    .config(routes)
    .config(translations)
    .service('PeopleService', PeopleService);

// Attach to the page document
bootstrap(document, ['app']);

