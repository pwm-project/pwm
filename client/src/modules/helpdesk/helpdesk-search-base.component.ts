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


import SearchResult from '../../models/search-result.model';
import {isArray, isString, IPromise, IQService, IScope, ITimeoutService} from 'angular';
import {IPerson} from '../../models/person.model';
import {IHelpDeskConfigService} from '../../services/helpdesk-config.service';
import LocalStorageService from '../../services/local-storage.service';
import PromiseService from '../../services/promise.service';
import {IHelpDeskService} from '../../services/helpdesk.service';
import IPwmService from '../../services/pwm.service';
import {IAdvancedSearchConfig} from '../../services/base-config.service';

let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');
let recentVerificationsDialogTemplateUrl = require('./recent-verifications-dialog.template.html');

export default abstract class HelpDeskSearchBaseComponent {
    advancedSearch = false;
    advancedSearchTags: any[];
    advancedSearchEnabled: boolean;
    advancedSearchMaxRows: number;
    columnConfiguration: any;
    errorMessage: string;
    inputDebounce: number;
    protected pendingRequests: IPromise<any>[] = [];
    photosEnabled: boolean;
    query: string;
    queries: any[];
    initialQueryKey: string;
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
                protected pwmService: IPwmService) {
        this.searchTextLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_TEXT;
        this.searchViewLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_VIEW;

        this.inputDebounce = this.pwmService.ajaxTypingWait;

        $scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            this.onSearchTextChange(newValue, oldValue);
        });
    }

    protected initialize(): void {
        this.query = this.getSearchText();

        this.configService.verificationsEnabled().then((verificationsEnabled: boolean) => {
            this.verificationsEnabled = verificationsEnabled;
        });

        this.configService.advancedSearchConfig().then((advancedSearchConfig: IAdvancedSearchConfig) => {
            this.advancedSearchEnabled = advancedSearchConfig.enabled;
            this.advancedSearchTags = advancedSearchConfig.attributes;
            this.advancedSearchMaxRows = advancedSearchConfig.maxRows;

            // Save the first attribute to use as the initial selection of new query rows
            if (this.advancedSearchTags && this.advancedSearchTags.length > 0) {
                this.initialQueryKey = this.advancedSearchTags[0].attribute;
            }
        });

        // Once <ias-search-box> from ng-ias allows the autofocus attribute, we can remove this code
        this.$timeout(() => {
            document.getElementsByTagName('input')[0].focus();
        });
    }

    getMessage(): string {
        return this.errorMessage || this.searchMessage;
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

    abstract fetchData(): void;

    protected clearSearch(): void {
        this.query = null;
        this.queries = [{key: this.initialQueryKey, value: ''}];
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
        this.$state.go(state, { query: this.query });
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

    private onAdvancedSearchValueChanged() {
        this.initiateSearch();
    }

    removeSearchTag(tagIndex: number): void {
        this.queries.splice(tagIndex, 1);

        if (this.queries.length > 0) {
            this.initiateSearch();
        }
        else {
            this.clearSearch();
            this.advancedSearch = false;
        }
    }

    addSearchTag(): void {
        this.queries.push({key: this.initialQueryKey, value: ''});
    }

    protected selectPerson(person: IPerson): void {
        this.IasDialogService
            .open({
                controller: 'VerificationsDialogController as $ctrl',
                templateUrl: verificationsDialogTemplateUrl,
                locals: {
                    personUserKey: person.userKey,
                    search: true
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
        this.advancedSearch = true;
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
