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


import {IQService, IScope, ITimeoutService} from 'angular';
import HelpDeskSearchBaseComponent from './helpdesk-search-base.component';
import {Component} from '../../component';
import SearchResult from '../../models/search-result.model';
import {IHelpDeskConfigService} from '../../services/helpdesk-config.service';
import LocalStorageService from '../../services/local-storage.service';
import PromiseService from '../../services/promise.service';
import {IHelpDeskService} from '../../services/helpdesk.service';
import IPwmService from '../../services/pwm.service';
import CommonSearchService from '../../services/common-search.service';

@Component({
    stylesheetUrl: require('./helpdesk-search.component.scss'),
    templateUrl: require('./helpdesk-search-table.component.html')
})
export default class HelpDeskSearchTableComponent extends HelpDeskSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [
        '$q',
        '$scope',
        '$state',
        '$stateParams',
        '$timeout',
        '$translate',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'LocalStorageService',
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
                configService: IHelpDeskConfigService,
                helpDeskService: IHelpDeskService,
                IasDialogService: any,
                localStorageService: LocalStorageService,
                promiseService: PromiseService,
                pwmService: IPwmService,
                commonSearchService: CommonSearchService) {
        super($q, $scope, $state, $stateParams, $timeout, $translate, configService, helpDeskService, IasDialogService,
              localStorageService, promiseService, pwmService, commonSearchService);
    }

    $onInit() {
        this.initialize().then(() => {
            this.fetchData();
        });

        // The table columns are dynamic and configured via a service
        this.configService.getColumnConfig().then(
            (columnConfiguration: any) => {
                this.columnConfiguration = Object.keys(columnConfiguration).reduce(
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
                this.setErrorMessage(error);
            });
    }

    fetchData() {
        let searchResult = this.fetchSearchData();
        if (searchResult) {
            searchResult.then(this.onSearchResult.bind(this));
        }
    }

    gotoCardsView(): void {
        this.toggleView('search.cards');
    }

    private onSearchResult(searchResult: SearchResult): void {
        this.searchResult = searchResult;
    }
}
