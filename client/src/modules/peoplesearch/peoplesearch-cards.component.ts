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
import ElementSizeService from '../../ux/element-size.service';
import IPeopleSearchConfigService from '../../services/peoplesearch-config.service';
import IPeopleService from '../../services/people.service';
import IPwmService from '../../services/pwm.service';
import {isString, IAugmentedJQuery, IQService, IScope, ITimeoutService} from 'angular';
import LocalStorageService from '../../services/local-storage.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import { IPerson } from '../../models/person.model';
import PromiseService from '../../services/promise.service';
import SearchResult from '../../models/search-result.model';
import CommonSearchService from '../../services/common-search.service';

export enum PeopleSearchCardsSize {
    Small = 0,
    Medium = 365,
    Large = 450
}

@Component({
    stylesheetUrl: require('./peoplesearch-cards.component.scss'),
    templateUrl: require('./peoplesearch-cards.component.html')
})
export default class PeopleSearchCardsComponent extends PeopleSearchBaseComponent {
    photosEnabled: boolean;

    static $inject = [
        '$element',
        '$q',
        '$scope',
        '$state',
        '$stateParams',
        '$timeout',
        '$translate',
        'ConfigService',
        'LocalStorageService',
        'MfElementSizeService',
        'PeopleService',
        'PromiseService',
        'PwmService',
        'CommonSearchService'
    ];
    constructor(private $element: IAugmentedJQuery,
                $q: IQService,
                $scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                $timeout: ITimeoutService,
                $translate: angular.translate.ITranslateService,
                configService: IPeopleSearchConfigService,
                localStorageService: LocalStorageService,
                private elementSizeService: ElementSizeService,
                peopleService: IPeopleService,
                promiseService: PromiseService,
                pwmService: IPwmService,
                commonSearchService: CommonSearchService) {
        super($q,
            $scope,
            $state,
            $stateParams,
            $timeout,
            $translate,
            configService,
            localStorageService,
            peopleService,
            promiseService,
            pwmService,
            commonSearchService);
    }

    $onDestroy(): void {
        // TODO: remove $window click listener
    }

    $onInit(): void {
        this.initialize().then(() => {
            this.fetchData();
        });

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        });

        this.elementSizeService.watchWidth(this.$element, PeopleSearchCardsSize);
    }

    gotoTableView() {
        this.toggleView('search.table');
    }

    fetchData() {
        let searchResultPromise = this.fetchSearchData();
        if (searchResultPromise) {

            searchResultPromise.then(this.onSearchResult.bind(this));
        }
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
                        self.setErrorMessage(error);
                    })
                    .finally(() => {
                        self.removePendingRequest(promise);
                    });

                return promise;
            }
        );
    }
}
