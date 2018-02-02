/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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


import {Component} from '../component';
import {IPeopleService} from '../services/people.service';
import SearchResult from '../models/search-result.model';
import {IPromise, IQService} from 'angular';
import {IPerson} from '../models/person.model';
import DialogService from '../ux/ias-dialog.service';
import {IHelpDeskConfigService} from '../services/helpdesk-config.service';

let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');
let recentVerificationsDialogTemplateUrl = require('./recent-verifications-dialog.template.html');

@Component({
    stylesheetUrl: require('helpdesk/helpdesk-search.component.scss'),
    templateUrl: require('helpdesk/helpdesk-search.component.html')
})
export default class HelpDeskSearchComponent {
    columnConfiguration: any;
    protected pendingRequests: IPromise<any>[] = [];
    photosEnabled: boolean;
    query: string;
    searchResult: SearchResult;
    verificationsEnabled: boolean;
    view: string;

    static $inject = ['$q',
        'ConfigService',
        'IasDialogService',
        'PeopleService'
    ];

    constructor(private $q: IQService,
                private configService: IHelpDeskConfigService,
                private IasDialogService: DialogService,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        this.view = 'cards';

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        }); // TODO: only if in cards view (some other things are like that too)

        this.configService.verificationsEnabled().then((verificationsEnabled: boolean) => {
            this.verificationsEnabled = verificationsEnabled;
        });

        this.fetchData();
    }

    private fetchData() {
        let searchResultPromise = this.fetchSearchData();
        if (searchResultPromise) {

            searchResultPromise.then(this.onSearchResult.bind(this));
        }
    }

    private fetchSearchData(): IPromise<SearchResult> {
        // this.abortPendingRequests();
        this.searchResult = null;

        if (!this.query) {
            // this.clearSearch();
            return null;
        }

        const self = this;

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

    gotoCardsView(): void {
        if (this.view !== 'cards') {
            this.view = 'cards';
            this.fetchData();
        }
    }

    gotoTableView(): void {
        if (this.view !== 'table') {
            this.view = 'table';
            this.fetchData();
        }

        let self = this;

        // The table columns are dynamic and configured via a service
        this.configService.getColumnConfig().then(
            (columnConfiguration: any) => {
                self.columnConfiguration = columnConfiguration;
            },
            (error) => {
                // self.setErrorMessage(error);
            }); // TODO: remove self
    }

    private onSearchResult(searchResult: SearchResult): void {
        if (this.view === 'table') {
            this.searchResult = searchResult;
            return;
        }

        // Aborted request
        if (!searchResult) {
            return;
        }

        this.searchResult = new SearchResult({
            sizeExceeded: searchResult.sizeExceeded,
            searchResults: []
        });

        let self = this;

        this.pendingRequests = searchResult.people.map(
            (person: IPerson) => {
                // Store this promise because it is abortable
                let promise = this.peopleService.getPerson(person.userKey);

                promise
                    .then((person: IPerson) => {
                            // Aborted request
                            if (!person) {
                                return;
                            }

                            // searchResult may be overwritten by ESC->[LETTER] typed in after a search
                            // has started but before all calls to peopleService.getPerson have resolved
                            if (self.searchResult) {
                                self.searchResult.people.push(person);
                            }
                        },
                        (error) => {
                            // self.setErrorMessage(error);
                        })
                    .finally(() => {
                        // self.removePendingRequest(promise);
                    });

                return promise;
            }
        );  // TODO: this arg
    }

    onSearchTextChange(value: string): void {
        if (value === this.query) {
            return;
        }

        this.query = value;

        // this.storeSearchText();
        // this.clearSearchMessage();
        // this.clearErrorMessage();
        this.fetchData();
    }

    selectPerson(person: IPerson): void {
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

    showVerifications(): void {
        this.IasDialogService
            .open({
                controller: 'RecentVerificationsDialogController as $ctrl',
                templateUrl: recentVerificationsDialogTemplateUrl
            });
    }
}
