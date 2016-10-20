import { IPeopleService } from './services/people.service';

export default [
    '$stateProvider',
    '$urlRouterProvider',
    ($stateProvider: angular.ui.IStateProvider, $urlRouterProvider: angular.ui.IUrlRouterProvider) => {
        $urlRouterProvider.otherwise('/search/table');

        $stateProvider.state('search', { url: '/search', component: 'peopleSearch' });
        $stateProvider.state('search.table', { url: '/table', component: 'peopleSearchTable' });
        $stateProvider.state('search.cards', { url: '/cards', component: 'peopleSearchCards' });
        $stateProvider.state('orgchart', { url: '/orgchart/{personId}', component: 'orgChart', });
    }];
