/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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


import LocalStorageService from '../../services/local-storage.service';

export default [
    '$stateProvider',
    '$urlRouterProvider',
    (
        $stateProvider: angular.ui.IStateProvider,
        $urlRouterProvider: angular.ui.IUrlRouterProvider
    ) => {
        $urlRouterProvider.otherwise(
            ($injector: angular.auto.IInjectorService, $location: angular.ILocationService) => {
                let $state: angular.ui.IStateService = <angular.ui.IStateService>$injector.get('$state');
                let localStorageService: LocalStorageService =
                    <LocalStorageService>$injector.get('LocalStorageService');

                let storedView = localStorageService.getItem(localStorageService.keys.HELPDESK_SEARCH_VIEW);

                if (storedView) {
                    $state.go(storedView);
                }
                else {
                    $location.url('search/cards');
                }
            });

        $stateProvider.state('search', {
            url: '/search?query',
            abstract: true,
            template: '<div class="help-desk-search-component"><ui-view/></div>',
        });
        $stateProvider.state('search.cards', { url: '/cards', component: 'helpDeskSearchCards' });
        $stateProvider.state('search.table', { url: '/table', component: 'helpDeskSearchTable' });
        $stateProvider.state('details', { url: '/details/{personId}', component: 'helpDeskDetail' });
    }];
