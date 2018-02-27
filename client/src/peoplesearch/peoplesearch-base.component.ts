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


import { isArray, isString, IPromise, IQService, IScope } from 'angular';
import { IPeopleSearchConfigService } from '../services/peoplesearch-config.service';
import { IPeopleService } from '../services/people.service';
import IPwmService from '../services/pwm.service';
import LocalStorageService from '../services/local-storage.service';
import { IPerson } from '../models/person.model';
import PromiseService from '../services/promise.service';
import SearchResult from '../models/search-result.model';

const SEARCH_TEXT_LOCAL_STORAGE_KEY = 'searchText';

abstract class PeopleSearchBaseComponent {
    errorMessage: string;
    inputDebounce: number;
    orgChartEnabled: boolean;
    protected pendingRequests: IPromise<any>[] = [];
    searchMessage: string;
    searchResult: SearchResult;
    query: string;
    searchTextLocalStorageKey: string;
    searchViewLocalStorageKey: string;

    constructor(protected $q: IQService,
                protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected $translate: angular.translate.ITranslateService,
                protected configService: IPeopleSearchConfigService,
                protected localStorageService: LocalStorageService,
                protected peopleService: IPeopleService,
                protected promiseService: PromiseService,
                protected pwmService: IPwmService) {
        this.searchTextLocalStorageKey = this.localStorageService.keys.SEARCH_TEXT;
        this.searchViewLocalStorageKey = this.localStorageService.keys.SEARCH_VIEW;

        this.inputDebounce = this.pwmService.ajaxTypingWait;

        $scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            this.onSearchTextChange(newValue, oldValue);
        });
    }

    getMessage(): string {
        return this.errorMessage || this.searchMessage;
    }

    gotoOrgchart(): void {
        this.gotoState('orgchart.index');
    }

    private gotoState(state: string): void {
        this.$state.go(state, { query: this.query });
    }

    private onSearchTextChange(newValue: string, oldValue: string): void {
        if (newValue === oldValue) {
            return;
        }

        this.storeSearchText();
        this.clearSearchMessage();
        this.clearErrorMessage();
        this.fetchData();
    }

    selectPerson(person: IPerson): void {
        this.$state.go('.details', { personId: person.userKey, query: this.query });
    }

    // We are still loading if there are pending requests but no search results have come back yet
    get loading(): boolean {
        return !!this.pendingRequests.length && !this.searchResult;
    }

    protected abortPendingRequests() {
        for (let index = 0; index < this.pendingRequests.length; index++) {
            let pendingRequest = this.pendingRequests[index];
            this.promiseService.abort(pendingRequest);
        }

        this.pendingRequests = [];
    }

    protected removePendingRequest(promise: IPromise<any>) {
        let index = this.pendingRequests.indexOf(promise);

        if (index > -1) {
            this.pendingRequests.splice(index, 1);
        }
    }

    protected setErrorMessage(message: string) {
        this.errorMessage = message;
    }

    protected clearErrorMessage() {
        this.errorMessage = null;
    }

    // If message is a string it will be translated. If it is a promise it will assign the string from the resolved
    // promise
    protected setSearchMessage(translationKey: string) {
        if (!translationKey) {
            this.clearSearchMessage();
            return;
        }

        const self = this;
        this.$translate(translationKey.toString())
            .then((translation: string) => {
            self.searchMessage = translation;
        });
    }

    protected clearSearch(): void {
        this.query = null;
        this.searchResult = null;
        this.clearErrorMessage();
        this.clearSearchMessage();
        this.abortPendingRequests();
    }

    protected clearSearchMessage(): void  {
        this.searchMessage = null;
    }

    abstract fetchData(): void;

    protected fetchSearchData(): IPromise<void | SearchResult> {
        this.abortPendingRequests();
        this.searchResult = null;

        if (!this.query) {
            this.clearSearch();
            return null;
        }

        const self = this;

        let promise = this.peopleService.search(this.query);

        this.pendingRequests.push(promise);

        return promise
            .then(
                (searchResult: SearchResult) => {
                    self.clearErrorMessage();
                    self.clearSearchMessage();

                    // Aborted request
                    if (!searchResult) {
                        return;
                    }

                    // Too many results returned
                    if (searchResult.sizeExceeded) {
                        self.setSearchMessage('Display_SearchResultsExceeded');
                    }

                    // No results returned. Not an else if statement so that the more important message is presented
                    if (!searchResult.people.length) {
                        self.setSearchMessage('Display_SearchResultsNone');
                    }

                    return this.$q.resolve(searchResult);
                },
                (error) => {
                    self.setErrorMessage(error);
                    self.clearSearchMessage();
                })
            .finally(() => {
                self.removePendingRequest(promise);
            });
    }

    protected initialize(): void {
        // Determine whether org-chart should appear
        this.configService.orgChartEnabled().then((orgChartEnabled: boolean) => {
            this.orgChartEnabled = orgChartEnabled;
        });

        this.query = this.getSearchText();
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

    protected storeSearchText(): void {
        this.localStorageService.setItem(this.searchTextLocalStorageKey, this.query || '');
    }

    protected toggleView(state: string): void {
        this.storeSearchView(state);
        this.storeSearchText();
        this.gotoState(state);
    }

    private storeSearchView(state: string) {
        this.localStorageService.setItem(this.searchViewLocalStorageKey, state);
    }
}

export default PeopleSearchBaseComponent;
