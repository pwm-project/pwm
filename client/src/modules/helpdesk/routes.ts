/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
