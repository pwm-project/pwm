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


import { IPeopleSearchConfigService } from './services/peoplesearch-config.service';
import { IQService } from 'angular';
import LocalStorageService from './services/local-storage.service';

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

                let storedView = localStorageService.getItem(localStorageService.keys.SEARCH_VIEW);

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
        $stateProvider.state('orgchart', { url: '/orgchart?query',
            abstract: true,
            template: '<ui-view/>',
            resolve: {
                enabled: [
                    '$q',
                    'ConfigService',
                    ($q: IQService, configService: IPeopleSearchConfigService) => {
                        let deferred = $q.defer();

                        configService
                            .orgChartEnabled()
                            .then((orgChartEnabled: boolean) => {
                                if (!orgChartEnabled) {
                                    deferred.reject('OrgChart disabled');
                                }
                                else {
                                    deferred.resolve();
                                }
                            });

                        return deferred.promise;
                    }]
            }
        });
        $stateProvider.state('orgchart.index', { url: '', component: 'orgChartSearch' });
        $stateProvider.state('orgchart.search', { url: '/{personId}', component: 'orgChartSearch' });
        $stateProvider.state('orgchart.search.details', { url: '/details', component: 'personDetailsDialogComponent' });
    }];
