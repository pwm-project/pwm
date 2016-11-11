export default [
    '$stateProvider',
    '$urlRouterProvider',
    '$locationProvider',
    (
        $stateProvider: angular.ui.IStateProvider,
        $urlRouterProvider: angular.ui.IUrlRouterProvider,
        $locationProvider: angular.ILocationProvider
    ) => {
        $urlRouterProvider.otherwise('/search/cards');
        $locationProvider.html5Mode(true);

        $stateProvider.state('search', {
            url: '/search?query',
            abstract: true,
            template: '<div class="people-search-component"><ui-view/></div>',
            reloadOnSearch: false
        });
        $stateProvider.state('search.table', { url: '/table', component: 'peopleSearchTable' });
        $stateProvider.state('search.cards', { url: '/cards', component: 'peopleSearchCards' });
        $stateProvider.state('orgchart', { url: '/orgchart/{personId}', component: 'orgChartSearch' });
    }];
