/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


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
        $locationProvider.html5Mode({
            enabled: true,
            requireBase: false
        });

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
        $stateProvider.state('orgchart.search.details', { url: '/details', component: 'personDetailsDialogComponent' });
    }];
