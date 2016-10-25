import { module } from 'angular';
import OrgChartComponent from './orgchart.component';
import PeopleSearchService from './peoplesearch.service';
import PeopleSearchComponent from './peoplesearch.component';
import PeopleSearchTableComponent from './peoplesearch-table.component';
import PeopleSearchCardsComponent from './peoplesearch-cards.component';

var moduleName = 'people-search';

module(moduleName, [ ])
    .service('PeopleSearchService', PeopleSearchService)
    .component('orgChart', OrgChartComponent)
    .component('peopleSearch', PeopleSearchComponent)
    .component('peopleSearchTable', PeopleSearchTableComponent)
    .component('peopleSearchCards', PeopleSearchCardsComponent);

export default moduleName;
