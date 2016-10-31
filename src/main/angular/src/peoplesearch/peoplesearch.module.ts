import { module } from 'angular';
import OrgChartComponent from './orgchart.component';
import PeopleSearchService from './peoplesearch.service';
import PeopleSearchComponent from './peoplesearch.component';
import PeopleSearchTableComponent from './peoplesearch-table.component';
import PeopleSearchCardsComponent from './peoplesearch-cards.component';
import PersonCardComponent from './person-card.component';
import uxModule from '../ux/ux.module';

require('./peoplesearch.scss');

var moduleName = 'people-search';

module(moduleName, [ uxModule ])
    .service('PeopleSearchService', PeopleSearchService)
    .component('orgChart', OrgChartComponent)
    .component('personCard', PersonCardComponent)
    .component('peopleSearch', PeopleSearchComponent)
    .component('peopleSearchTable', PeopleSearchTableComponent)
    .component('peopleSearchCards', PeopleSearchCardsComponent);

export default moduleName;
