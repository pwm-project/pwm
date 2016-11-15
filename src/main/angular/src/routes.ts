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
        });
        $stateProvider.state('search.table', { url: '/table', component: 'peopleSearchTable' });
        $stateProvider.state('search.cards', { url: '/cards', component: 'peopleSearchCards' });
        $stateProvider.state('search.table.details', {
            url: '/details/{personId}',
            component: 'personDetailsDialogComponent'
        });
        $stateProvider.state('search.cards.details', {
            url: '/details/{personId}',
            component: 'personDetailsDialogComponent'
        });
        $stateProvider.state('orgchart', { url: '/orgchart', abstract: true, template: '<ui-view/>' });
        $stateProvider.state('orgchart.index', { url: '', component: 'orgChartSearch' });
        $stateProvider.state('orgchart.search', { url: '/{personId}', component: 'orgChartSearch' });
    }];
