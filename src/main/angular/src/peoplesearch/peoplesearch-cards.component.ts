/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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


import { Component } from '../component';
import ElementSizeService from '../ux/element-size.service';
import IConfigService from '../services/config.service';
import IPeopleService from '../services/people.service';
import IPwmService from '../services/pwm.service';
import { isString, IAugmentedJQuery, IQService, IScope } from 'angular';
import LocalStorageService from '../services/local-storage.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import Person from '../models/person.model';
import PromiseService from '../services/promise.service';
import SearchResult from '../models/search-result.model';

export enum PeopleSearchCardsSize {
    Small = 0,
    Medium = 365,
    Large = 450
}

@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-cards.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-cards.component.html')
})
export default class PeopleSearchCardsComponent extends PeopleSearchBaseComponent {
    photosEnabled: boolean;

    static $inject = [
        '$element',
        '$q',
        '$scope',
        '$state',
        '$stateParams',
        '$translate',
        'ConfigService',
        'LocalStorageService',
        'MfElementSizeService',
        'PeopleService',
        'PromiseService',
        'PwmService'
    ];
    constructor(private $element: IAugmentedJQuery,
                $q: IQService,
                $scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                $translate: angular.translate.ITranslateService,
                configService: IConfigService,
                localStorageService: LocalStorageService,
                private elementSizeService: ElementSizeService,
                peopleService: IPeopleService,
                promiseService: PromiseService,
                pwmService: IPwmService) {
        super($q,
            $scope,
            $state,
            $stateParams,
            $translate,
            configService,
            localStorageService,
            peopleService,
            promiseService,
            pwmService);
    }

    $onDestroy(): void {
        // TODO: remove $window click listener
    }

    $onInit(): void {
        this.initialize();
        this.fetchData();

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
            (person: Person) => {
                // Store this promise because it is abortable
                let promise = this.peopleService.getPerson(person.userKey);

                promise
                    .then((person: Person) => {
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
