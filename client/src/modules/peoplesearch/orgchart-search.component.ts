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


import { Component } from '../../component';
import { IPeopleSearchConfigService } from '../../services/peoplesearch-config.service';
import { IPeopleService } from '../../services/people.service';
import IPwmService from '../../services/pwm.service';
import {isArray, isString, IPromise, IQService, IScope, ITimeoutService} from 'angular';
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

    static $inject = [
        '$state',
        '$stateParams',
        '$timeout',
        'ConfigService',
        'LocalStorageService',
        'PeopleService',
        'PwmService'
    ];
    constructor(private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private $timeout: ITimeoutService,
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
}
