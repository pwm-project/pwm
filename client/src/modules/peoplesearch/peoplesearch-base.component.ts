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

import * as angular from 'angular';
import {isArray, isString, IPromise, IQService, IScope, ITimeoutService} from 'angular';
import { IPeopleSearchConfigService } from '../../services/peoplesearch-config.service';
import { IPeopleService } from '../../services/people.service';
import IPwmService from '../../services/pwm.service';
import LocalStorageService from '../../services/local-storage.service';
import { IPerson } from '../../models/person.model';
import PromiseService from '../../services/promise.service';
import SearchResult from '../../models/search-result.model';
import {IAdvancedSearchConfig, IAdvancedSearchQuery, IAttributeMetadata} from '../../services/base-config.service';
import CommonSearchService from '../../services/common-search.service';

abstract class PeopleSearchBaseComponent {
    advancedSearch = false;
    advancedSearchTags = {};
    advancedSearchEnabled: boolean;
    advancedSearchMaxRows: number;
    errorMessage: string;
    inputDebounce: number;
    orgChartEnabled: boolean;
    protected pendingRequests: IPromise<any>[] = [];
    searchMessage: string;
    searchResult: SearchResult;
    query: string;
    queries: IAdvancedSearchQuery[];
    searchTextLocalStorageKey: string;
    searchViewLocalStorageKey: string;

    constructor(protected $q: IQService,
                protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected $timeout: ITimeoutService,
                protected $translate: angular.translate.ITranslateService,
                protected configService: IPeopleSearchConfigService,
                protected localStorageService: LocalStorageService,
                protected peopleService: IPeopleService,
                protected promiseService: PromiseService,
                protected pwmService: IPwmService,
                protected commonSearchService: CommonSearchService) {
        this.searchTextLocalStorageKey = this.localStorageService.keys.SEARCH_TEXT;
        this.searchViewLocalStorageKey = this.localStorageService.keys.SEARCH_VIEW;

        this.inputDebounce = this.pwmService.ajaxTypingWait;
    }

    getMessage(): string {
        return this.errorMessage || this.searchMessage;
    }

    gotoOrgchart(): void {
        this.gotoState('orgchart.index');
    }

    private gotoState(state: string): void {
        this.$state.go(state);
    }

    private initiateSearch() {
        this.clearSearchMessage();
        this.clearErrorMessage();
        this.fetchData();
    }

    private onAdvancedSearchAttributeChanged(query: IAdvancedSearchQuery) {
        // Make sure we set the default value if the type is select
        const attributeMetadata: IAttributeMetadata = this.advancedSearchTags[query.key];
        if (attributeMetadata.type == 'select') {
            query.value = this.commonSearchService.getDefaultValue(attributeMetadata);
        }

        this.commonSearchService.setPsAdvSearchQueries(this.queries);
        this.initiateSearch();
    }

    private onAdvancedSearchAttributeValueChanged() {
        this.commonSearchService.setPsAdvSearchQueries(this.queries);
        this.initiateSearch();
    }

    private onAdvancedSearchValueChanged() {
        this.commonSearchService.setPsAdvSearchQueries(this.queries);
        this.initiateSearch();
    }

    private onSearchTextChange(newValue: string, oldValue: string): void {
        if (newValue === oldValue) {
            return;
        }

        this.storeSearchText();
        this.initiateSearch();
    }

    removeSearchTag(tagIndex: number): void {
        this.queries.splice(tagIndex, 1);
        this.commonSearchService.setPsAdvSearchQueries(this.queries);

        if (this.queries.length > 0) {
            this.initiateSearch();
        }
        else {
            this.clearSearch();
            this.advancedSearch = false;
            this.commonSearchService.setPsAdvancedSearchActive(this.advancedSearch);
        }
    }

    addSearchTag(): void {
        const firstTagName = Object.keys(this.advancedSearchTags)[0];
        const attributeMetaData: IAttributeMetadata = this.advancedSearchTags[firstTagName];

        const query: IAdvancedSearchQuery = {
            key: attributeMetaData.attribute,
            value: this.commonSearchService.getDefaultValue(attributeMetaData),
        };

        this.queries.push(query);
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
        this.queries = [];
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

        const self = this;
        let promise;

        if (this.advancedSearch) {
            if (!this.queries || (this.queries.length === 1 && !this.queries[0].key)) {
                this.clearSearch();
                return null;
            }

            const keys = new Set();
            for (let searchQuery of this.queries) {
                keys.add(searchQuery.key);
            }

            const duplicateSearchAttrsFound = keys.size < this.queries.length;
            if (duplicateSearchAttrsFound) {
                this.$translate('Display_SearchAttrsUnique')
                    .then((translation: string) => {
                        this.searchMessage = translation;
                    });

                return null;
            }

            promise = this.peopleService.advancedSearch(this.queries);
        }
        else {
            if (!this.query) {
                this.clearSearch();
                return null;
            }

            promise = this.peopleService.search(this.query);
        }

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

                    return searchResult;
                },
                (error) => {
                    self.setErrorMessage(error);
                    self.clearSearchMessage();
                })
            .finally(() => {
                self.removePendingRequest(promise);
            });
    }

    protected initialize(): IPromise<void> {
        return this.$q.all(
            [
                // Determine whether org-chart should appear
                this.configService.orgChartEnabled().then((orgChartEnabled: boolean) => {
                    this.orgChartEnabled = orgChartEnabled;
                }),
                this.configService.advancedSearchConfig().then((advancedSearchConfig: IAdvancedSearchConfig) => {
                    this.advancedSearchEnabled = advancedSearchConfig.enabled;
                    this.advancedSearchMaxRows = advancedSearchConfig.maxRows;

                    for (let advancedSearchTag of advancedSearchConfig.attributes) {
                        this.advancedSearchTags[advancedSearchTag.attribute] = advancedSearchTag;
                    }
                })
            ]
        ).then(result => {
            const searchQuery = this.getSearchQuery();
            if (searchQuery) {
                // A search query has been passed in, disregard the current search state
                this.query = searchQuery;
                this.advancedSearch = false;
                this.storeSearchText();
                this.commonSearchService.setPsAdvancedSearchActive(this.advancedSearch);
                this.commonSearchService.setPsAdvSearchQueries([]);
            } else {
                this.query = this.getSearchText();
                this.advancedSearch = this.commonSearchService.isPsAdvancedSearchActive();
                this.queries = this.commonSearchService.getPsAdvSearchQueries();
                if (this.queries.length === 0) {
                    this.addSearchTag();
                }
            }

            // Once <ias-search-box> from ng-ias allows the autofocus attribute, we can remove this code
            this.$timeout(() => {
                document.getElementsByTagName('input')[0].focus();
            });

            this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
                this.onSearchTextChange(newValue, oldValue);
            });
        });
    }

    private getSearchQuery(): string {
        let param: string = this.$stateParams['query'];
        // If multiple query parameters are defined, use the first one
        if (isArray(param)) {
            param = param[0].trim();
        }
        else if (isString(param)) {
            param = param.trim();
        }

        return param;
    }

    private getSearchText(): string {
        return this.localStorageService.getItem(this.searchTextLocalStorageKey);
    }

    protected storeSearchText(): void {
        this.localStorageService.setItem(this.searchTextLocalStorageKey, this.query || '');
    }

    enableAdvancedSearch(): void {
        this.clearSearch();
        this.addSearchTag();
        this.advancedSearch = true;
        this.commonSearchService.setPsAdvancedSearchActive(this.advancedSearch);
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
