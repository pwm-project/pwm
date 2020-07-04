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


import { Component } from '../../component';
import { IPeopleSearchConfigService } from '../../services/peoplesearch-config.service';
import IPeopleService from '../../services/people.service';
import IPwmService from '../../services/pwm.service';
import {IQService, IScope, ITimeoutService} from 'angular';
import LocalStorageService from '../../services/local-storage.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import PromiseService from '../../services/promise.service';
import SearchResult from '../../models/search-result.model';
import CommonSearchService from '../../services/common-search.service';

@Component({
    stylesheetUrl: require('./peoplesearch-table.component.scss'),
    templateUrl: require('./peoplesearch-table.component.html')
})
export default class PeopleSearchTableComponent extends PeopleSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [
        '$q',
        '$scope',
        '$state',
        '$stateParams',
        '$timeout',
        '$translate',
        'ConfigService',
        'LocalStorageService',
        'PeopleService',
        'PromiseService',
        'PwmService',
        'CommonSearchService'
    ];
    constructor($q: IQService,
                $scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                $timeout: ITimeoutService,
                $translate: angular.translate.ITranslateService,
                configService: IPeopleSearchConfigService,
                localStorageService: LocalStorageService,
                peopleService: IPeopleService,
                promiseService: PromiseService,
                pwmService: IPwmService,
                commonSearchService: CommonSearchService) {
        super($q,
            $scope,
            $state,
            $stateParams,
            $timeout,
            $translate,
            configService,
            localStorageService,
            peopleService,
            promiseService,
            pwmService,
            commonSearchService);
    }

    $onInit(): void {
        this.initialize().then(() => {
            this.fetchData();
        });

        let self = this;

        // The table columns are dynamic and configured via a service
        this.configService.getColumnConfig().then(
            (columnConfiguration: any) => {
                self.columnConfiguration = Object.keys(columnConfiguration).reduce(
                    function(accumulator, columnId) {
                        accumulator[columnId] = {
                            label: columnConfiguration[columnId],
                            visible: true
                        };

                        return accumulator;
                    },
                    {});
            },
            (error) => {
                self.setErrorMessage(error);
            });
    }

    gotoCardsView() {
        this.toggleView('search.cards');
    }

    fetchData() {
        let searchResult = this.fetchSearchData();
        if (searchResult) {
            searchResult.then(this.onSearchResult.bind(this));
        }
    }

    private onSearchResult(searchResult: SearchResult): void {
        this.searchResult = searchResult;
    }
}
