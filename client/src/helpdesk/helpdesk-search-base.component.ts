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


import {IPeopleService} from '../services/people.service';
import SearchResult from '../models/search-result.model';
import {isArray, isString, IPromise, IQService, IScope} from 'angular';
import {IPerson} from '../models/person.model';
import {IHelpDeskConfigService} from '../services/helpdesk-config.service';
import LocalStorageService from '../services/local-storage.service';

let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');
let recentVerificationsDialogTemplateUrl = require('./recent-verifications-dialog.template.html');

export default abstract class HelpDeskSearchBaseComponent {
    columnConfiguration: any;
    protected pendingRequests: IPromise<any>[] = [];
    photosEnabled: boolean;
    query: string;
    searchResult: SearchResult;
    searchTextLocalStorageKey: string;
    searchViewLocalStorageKey: string;
    verificationsEnabled: boolean;
    view: string;

    constructor(protected $q: IQService,
                protected $scope: IScope,
                protected $stateParams: angular.ui.IStateParamsService,
                protected configService: IHelpDeskConfigService,
                protected IasDialogService: any,
                protected localStorageService: LocalStorageService,
                protected peopleService: IPeopleService) {
        this.searchTextLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_TEXT;
        this.searchViewLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_VIEW;

        $scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            this.onSearchTextChange(newValue, oldValue);
        });
    }

    protected initialize(): void {
        this.query = this.getSearchText();

        this.configService.verificationsEnabled().then((verificationsEnabled: boolean) => {
            this.verificationsEnabled = verificationsEnabled;
        });
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

    protected fetchSearchData(): IPromise<void | SearchResult> {
        // this.abortPendingRequests();
        this.searchResult = null;

        if (!this.query) {
            // this.clearSearch();
            return null;
        }

        let promise = this.peopleService.search(this.query);
        this.pendingRequests.push(promise);

        return promise
            .then(
                (searchResult: SearchResult) => {
                    // self.clearErrorMessage();
                    // self.clearSearchMessage();

                    // Aborted request
                    if (!searchResult) {
                        return;
                    }

                    // Too many results returned
                    // if (searchResult.sizeExceeded) {
                    //     self.setSearchMessage('Display_SearchResultsExceeded');
                    // }

                    // No results returned. Not an else if statement so that the more important message is presented
                    // if (!searchResult.people.length) {
                    //     self.setSearchMessage('Display_SearchResultsNone');
                    // }

                    return this.$q.resolve(searchResult);
                },
                (error) => {
                    /*self.setErrorMessage(error);
                    self.clearSearchMessage();*/
                })
            .finally(() => {
                // self.removePendingRequest(promise);
            });
    }

    private onSearchTextChange(newValue: string, oldValue: string): void {
        if (newValue === oldValue) {
            return;
        }

        this.storeSearchText();
        // this.clearSearchMessage();
        // this.clearErrorMessage();
        this.fetchData();
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
}
