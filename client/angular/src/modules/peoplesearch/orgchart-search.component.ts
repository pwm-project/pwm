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
import { IPeopleService } from '../../services/people.service';
import IPwmService from '../../services/pwm.service';
import {isArray, isString, IPromise, IQService, IScope, ITimeoutService, IWindowService} from 'angular';
import LocalStorageService from '../../services/local-storage.service';
import IOrgChartData from '../../models/orgchart-data.model';
import { IPerson } from '../../models/person.model';

@Component({
    stylesheetUrl: require('./orgchart-search.component.scss'),
    templateUrl: require('./orgchart-search.component.html')
})
export default class OrgChartSearchComponent {
    directReports: IPerson[];
    inputDebounce: number;
    managementChain: IPerson[];
    assistant: IPerson;
    person: IPerson;
    photosEnabled: boolean;
    managementChainLimit: number;
    query: string;
    searchTextLocalStorageKey: string;
    printEnabled: boolean;

    static $inject = [
        '$state',
        '$stateParams',
        '$timeout',
        '$window',
        'ConfigService',
        'LocalStorageService',
        'PeopleService',
        'PwmService'
    ];
    constructor(private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private $timeout: ITimeoutService,
                private $window: IWindowService,
                private configService: IPeopleSearchConfigService,
                private localStorageService: LocalStorageService,
                private peopleService: IPeopleService,
                private pwmService: IPwmService) {
        this.searchTextLocalStorageKey = this.localStorageService.keys.SEARCH_TEXT;
        this.inputDebounce = this.pwmService.ajaxTypingWait;
    }

    $onInit(): void {
        const self = this;

        this.configService.photosEnabled().then(
            (photosEnabled: boolean) => {
                this.photosEnabled = photosEnabled;
            });

        this.configService.getOrgChartMaxParents().then(
            (orgChartMaxParents: number) => {
                this.managementChainLimit = orgChartMaxParents;
            });

        this.configService.printingEnabled().then(
            (printingEnabled: boolean) => {
                this.printEnabled = printingEnabled;
            });

        this.query = this.getSearchText();

        let personId: string = this.$stateParams['personId'];

        this.fetchOrgChartData(personId)
            .then((orgChartData: IOrgChartData) => {
                if (!orgChartData) {
                    return;
                }

                // Override personId in case it was undefined
                personId = orgChartData.self.userKey;

                if (orgChartData.assistant) {
                    self.assistant = orgChartData.assistant;
                }

                self.peopleService.getPerson(personId)
                    .then((person: IPerson) => {
                            self.person = person;
                        },
                        (error) => {
                            // TODO: handle error
                        });

                self.peopleService.getManagementChain(personId, self.managementChainLimit)
                    .then((managementChain: IPerson[]) => {
                            self.managementChain = managementChain;
                        },
                        (error) => {
                            // TODO: handle error
                        });

                self.peopleService.getDirectReports(personId)
                    .then((directReports: IPerson[]) => {
                            self.directReports = directReports;
                        },
                        (error) => {
                            // TODO: handle error
                        });
            },
            (error) => {
                // TODO: handle error
            });

        // Once <ias-search-box> from ng-ias allows the autofocus attribute, we can remove this code
        this.$timeout(() => {
            document.getElementsByTagName('input')[0].focus();
        });
    }

    autoCompleteSearch(query: string): IPromise<IPerson[]> {
        this.storeSearchText(query);
        return this.peopleService.autoComplete(query);
    }

    gotoSearchState(state: string) {
        this.$state.go(state, { query: this.query });
    }

    onAutoCompleteItemSelected(person: IPerson): void {
        this.storeSearchText(null);
        this.$state.go('orgchart.search', { personId: person.userKey, query: null });
    }

    private fetchOrgChartData(personId): IPromise<IOrgChartData> {
        return this.peopleService.getOrgChartData(personId, true);
    }

    private getSearchText(): string {
        let param: string = this.$stateParams['query'];
        // If multiple query parameters are defined, use the first one
        if (isArray(param)) {
            param = param[0].trim();
        }
        else if (isString(param)) {
            param = param.trim();
        }

        return param || this.localStorageService.getItem(this.searchTextLocalStorageKey);
    }

    protected storeSearchText(query): void {
        this.localStorageService.setItem(this.searchTextLocalStorageKey, query || '');
    }

    private printOrgChart(): void {
        this.$window.print();
    }
}
