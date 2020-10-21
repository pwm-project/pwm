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


import {Component} from '../../component';
import {IQService, IScope, ITimeoutService} from 'angular';
import {IHelpDeskConfigService} from '../../services/helpdesk-config.service';
import LocalStorageService from '../../services/local-storage.service';
import HelpDeskSearchBaseComponent from './helpdesk-search-base.component';
import SearchResult from '../../models/search-result.model';
import {IPerson} from '../../models/person.model';
import PromiseService from '../../services/promise.service';
import {IHelpDeskService} from '../../services/helpdesk.service';
import IPwmService from '../../services/pwm.service';
import CommonSearchService from '../../services/common-search.service';

@Component({
    stylesheetUrl: require('./helpdesk-search.component.scss'),
    templateUrl: require('./helpdesk-search-cards.component.html')
})
export default class HelpDeskSearchCardsComponent extends HelpDeskSearchBaseComponent {
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

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        });
    }

    fetchData() {
        let searchResult = this.fetchSearchData();
        if (searchResult) {
            searchResult.then(this.onSearchResult.bind(this));
        }
    }

    gotoTableView(): void {
        this.toggleView('search.table');
    }

    private onSearchResult(searchResult: SearchResult): void {
        // Aborted request
        if (!searchResult) {
            return;
        }

        this.searchResult = new SearchResult({
            sizeExceeded: searchResult.sizeExceeded,
            searchResults: []
        });

        this.pendingRequests = searchResult.people.map(
            (person: IPerson) => {
                // Store this promise because it is abortable
                let promise = this.helpDeskService.getPersonCard(person.userKey);

                promise
                    .then(function(person: IPerson) {
                            // Aborted request
                            if (!person) {
                                return;
                            }

                            // searchResult may be overwritten by ESC->[LETTER] typed in after a search
                            // has started but before all calls to helpdeskService.getPersonCard have resolved
                            if (this.searchResult) {
                                this.searchResult.people.push(person);
                            }
                        }.bind(this),
                        (error) => {
                            this.setErrorMessage(error);
                        })
                    .finally(() => {
                        this.removePendingRequest(promise);
                    });

                return promise;
            },
            this
        );
    }
}
