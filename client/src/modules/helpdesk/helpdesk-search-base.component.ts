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


import SearchResult from '../../models/search-result.model';
import {isArray, isString, IPromise, IQService, IScope, ITimeoutService} from 'angular';
import {IPerson} from '../../models/person.model';
import {IHelpDeskConfigService} from '../../services/helpdesk-config.service';
import LocalStorageService from '../../services/local-storage.service';
import PromiseService from '../../services/promise.service';
import {IHelpDeskService} from '../../services/helpdesk.service';
import IPwmService from '../../services/pwm.service';
import {IAdvancedSearchConfig, IAdvancedSearchQuery, IAttributeMetadata} from '../../services/base-config.service';
import CommonSearchService from '../../services/common-search.service';


let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');
let recentVerificationsDialogTemplateUrl = require('./recent-verifications-dialog.template.html');

export default abstract class HelpDeskSearchBaseComponent {
    advancedSearch = false;
    advancedSearchTags = {};
    advancedSearchEnabled: boolean;
    advancedSearchMaxRows: number;
    columnConfiguration: any;
    errorMessage: string;
    inputDebounce: number;
    protected pendingRequests: IPromise<any>[] = [];
    photosEnabled: boolean;
    query: string;
    queries: IAdvancedSearchQuery[];
    searchMessage: string;
    searchResult: SearchResult;
    searchTextLocalStorageKey: string;
    searchViewLocalStorageKey: string;
    verificationsEnabled: boolean;

    constructor(protected $q: IQService,
                protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected $timeout: ITimeoutService,
                protected $translate: angular.translate.ITranslateService,
                protected configService: IHelpDeskConfigService,
                protected helpDeskService: IHelpDeskService,
                protected IasDialogService: any,
                protected localStorageService: LocalStorageService,
                protected promiseService: PromiseService,
                protected pwmService: IPwmService,
                protected commonSearchService: CommonSearchService) {
        this.searchTextLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_TEXT;
        this.searchViewLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_VIEW;

        this.inputDebounce = this.pwmService.ajaxTypingWait;
    }

    protected initialize(): IPromise<void> {
        return this.$q.all(
            [
                this.configService.verificationsEnabled().then((verificationsEnabled: boolean) => {
                    this.verificationsEnabled = verificationsEnabled;
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
                this.commonSearchService.setHdAdvancedSearchActive(this.advancedSearch);
                this.commonSearchService.setHdAdvSearchQueries([]);
            } else {
                this.query = this.getSearchText();
                this.advancedSearch = this.commonSearchService.isHdAdvancedSearchActive();
                this.queries = this.commonSearchService.getHdAdvSearchQueries();
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

    getMessage(): string {
        return this.errorMessage || this.searchMessage;
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

    abstract fetchData(): void;

    protected clearSearch(): void {
        this.query = null;
        this.queries = [];
        this.searchResult = null;
        this.clearErrorMessage();
        this.clearSearchMessage();
        this.abortPendingRequests();
    }

    protected fetchSearchData(): IPromise<void | SearchResult> {
        this.abortPendingRequests();
        this.searchResult = null;
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

            promise = this.helpDeskService.advancedSearch(this.queries);
        }
        else {
            if (!this.query) {
                this.clearSearch();
                return null;
            }

            promise = this.helpDeskService.search(this.query);
        }

        this.pendingRequests.push(promise);

        return promise
            .then(
                function(searchResult: SearchResult) {
                    this.clearErrorMessage();
                    this.clearSearchMessage();

                    // Aborted request
                    if (!searchResult) {
                        return;
                    }

                    // Too many results returned
                    if (searchResult.sizeExceeded) {
                        this.setSearchMessage('Display_SearchResultsExceeded');
                    }

                    // No results returned. Not an else if statement so that the more important message is presented
                    if (!searchResult.people.length) {
                        this.setSearchMessage('Display_SearchResultsNone');
                    }

                    return searchResult;
                }.bind(this),
                function(error) {
                    this.setErrorMessage(error);
                    this.clearSearchMessage();
                }.bind(this))
            .finally(function() {
                this.removePendingRequest(promise);
            }.bind(this));
    }

    private gotoState(state: string): void {
        this.$state.go(state);
    }

    private initiateSearch() {
        this.clearSearchMessage();
        this.clearErrorMessage();
        this.fetchData();
    }

    private onSearchTextChange(newValue: string, oldValue: string): void {
        if (newValue === oldValue) {
            return;
        }

        this.storeSearchText();
        this.initiateSearch();
    }

    protected abortPendingRequests() {
        for (let index = 0; index < this.pendingRequests.length; index++) {
            let pendingRequest = this.pendingRequests[index];
            this.promiseService.abort(pendingRequest);
        }

        this.pendingRequests = [];
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

    protected clearSearchMessage(): void  {
        this.searchMessage = null;
    }

    protected removePendingRequest(promise: IPromise<any>) {
        let index = this.pendingRequests.indexOf(promise);

        if (index > -1) {
            this.pendingRequests.splice(index, 1);
        }
    }

    private onAdvancedSearchAttributeChanged(query: IAdvancedSearchQuery) {
        // Make sure we set the default value if the type is select
        const attributeMetadata: IAttributeMetadata = this.advancedSearchTags[query.key];
        if (attributeMetadata.type == 'select') {
            query.value = this.commonSearchService.getDefaultValue(attributeMetadata);
        }

        this.commonSearchService.setHdAdvSearchQueries(this.queries);
        this.initiateSearch();
    }

    private onAdvancedSearchAttributeValueChanged() {
        this.commonSearchService.setHdAdvSearchQueries(this.queries);
        this.initiateSearch();
    }

    private onAdvancedSearchValueChanged() {
        this.commonSearchService.setHdAdvSearchQueries(this.queries);
        this.initiateSearch();
    }

    removeSearchTag(tagIndex: number): void {
        this.queries.splice(tagIndex, 1);
        this.commonSearchService.setHdAdvSearchQueries(this.queries);

        if (this.queries.length > 0) {
            this.initiateSearch();
        }
        else {
            this.clearSearch();
            this.advancedSearch = false;
            this.commonSearchService.setHdAdvancedSearchActive(this.advancedSearch);
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

    protected selectPerson(person: IPerson): void {
        this.IasDialogService
            .open({
                controller: 'VerificationsDialogController as $ctrl',
                templateUrl: verificationsDialogTemplateUrl,
                locals: {
                    personUserKey: person.userKey,
                    showRequiredOnly: true
                }
            });
    }

    protected showVerifications(): void {
        this.IasDialogService
            .open({
                controller: 'RecentVerificationsDialogController as $ctrl',
                templateUrl: recentVerificationsDialogTemplateUrl
            });
    }

    protected storeSearchText(): void {
        this.localStorageService.setItem(this.searchTextLocalStorageKey, this.query || '');
    }

    enableAdvancedSearch(): void {
        this.clearSearch();
        this.addSearchTag();
        this.advancedSearch = true;
        this.commonSearchService.setHdAdvancedSearchActive(this.advancedSearch);
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
