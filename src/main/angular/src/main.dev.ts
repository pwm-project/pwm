import { bootstrap, module } from 'angular';
import uiRouter from 'angular-ui-router';
import orgChartModule from './orgchart/orgchart.module';
import peopleSearchModule from './peoplesearch/peoplesearch.module';
import routes from './routes';
import PeopleService from './services/people.service.dev';

module('app', [
    uiRouter,
    orgChartModule,
    peopleSearchModule
])

    .config(routes)
    .service('PeopleService', PeopleService);

// Attach to the page document
bootstrap(document, ['app']);
