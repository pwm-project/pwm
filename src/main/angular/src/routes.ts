export default [
    '$stateProvider',
    '$urlRouterProvider',
    ($stateProvider: angular.ui.IStateProvider, $urlRouterProvider: angular.ui.IUrlRouterProvider) => {
        $urlRouterProvider.otherwise('/search/table');

        $stateProvider.state({ name: 'search', url: '/search', component: 'peopleSearch' });
        $stateProvider.state({ name: 'search.table', url: '/table', component: 'peopleSearchTable' });
        $stateProvider.state({ name: 'search.cards', url: '/cards', component: 'peopleSearchCards' });
        $stateProvider.state({ name: 'orgchart', url: '/orgchart/{personId}', component: 'orgChart' });
    }];
