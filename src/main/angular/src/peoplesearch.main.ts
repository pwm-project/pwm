/// <reference types="angular" />

// Note: I'd rather use imports for angular rather than the <reference> tag above, but I keep running into problems when angular-ui-router is loaded by SystemJS
//import "angular";
//import "angular-ui-router";

import { PeopleSearchService } from "./peoplesearch/peoplesearch.service";
import { PeopleSearchComponent } from "./peoplesearch/peoplesearch.component";
import { PeopleSearchTableComponent } from "./peoplesearch/peoplesearch-table.component";
import { PeopleSearchCardsComponent } from "./peoplesearch/peoplesearch-cards.component";
import { OrgChartComponent } from "./orgchart/orgchart.component";
import { OrgChartService } from "./orgchart/orgchart.service";

declare var PWM_PS: any;
declare var angular: angular.IAngularStatic;

angular.module('PeopleSearchApp', ['ui.router'])
    .service('peopleSearchService', PeopleSearchService)
    .service('orgChartService', OrgChartService)

    .component('peopleSearch', new PeopleSearchComponent())
    .component('peopleSearchTable', new PeopleSearchTableComponent())
    .component('peopleSearchCards', new PeopleSearchCardsComponent())
    .component('orgChart', new OrgChartComponent())

    .run(['peopleSearchService', (peopleSearchService) => {
        // Hack to make the PeopleSearchService available to existing PWM code
        PWM_PS.peopleSearchService = peopleSearchService;
    }])

    .config(['$stateProvider', '$urlRouterProvider', ($stateProvider, $urlRouterProvider) => {
        $urlRouterProvider.otherwise('/search/table');

        $stateProvider.state({ name: 'search', url: '/search', component: 'peopleSearch' });
        $stateProvider.state({ name: 'search.table', url: '/table', component: 'peopleSearchTable' });
        $stateProvider.state({ name: 'search.cards', url: '/cards', component: 'peopleSearchCards' });
        $stateProvider.state({ name: 'orgchart', url: '/orgchart/{userKey}', component: 'orgChart' });
    }]);

// Attach to the page document
angular.bootstrap(document, ['PeopleSearchApp']);
