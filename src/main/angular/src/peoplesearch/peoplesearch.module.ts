import { module } from 'angular';
import { DasherizeFilter } from './string.filters';
import { FullNameFilter } from './person.filters';
import OrgChartComponent from './orgchart.component';
import OrgChartSearchComponent from './orgchart-search.component';
import PeopleSearchService from './peoplesearch.service';
import PeopleSearchComponent from './peoplesearch.component';
import PeopleSearchTableComponent from './peoplesearch-table.component';
import PeopleSearchCardsComponent from './peoplesearch-cards.component';
import PersonCardComponent from './person-card.component';
import uxModule from '../ux/ux.module';

require('./peoplesearch.scss');

var moduleName = 'people-search';

module(moduleName, [
    'pascalprecht.translate',
    uxModule
])
    .service('PeopleSearchService', PeopleSearchService)
    .filter('dasherize', DasherizeFilter)
    .filter('fullName', FullNameFilter)
    .component('orgChart', OrgChartComponent)
    .component('orgChartSearch', OrgChartSearchComponent)
    .component('personCard', PersonCardComponent)
    .component('peopleSearch', PeopleSearchComponent)
    .component('peopleSearchTable', PeopleSearchTableComponent)
    .component('peopleSearchCards', PeopleSearchCardsComponent);

export default moduleName;
