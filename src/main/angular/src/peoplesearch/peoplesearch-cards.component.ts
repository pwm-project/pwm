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
import { IAugmentedJQuery, IQService, IScope } from 'angular';
import IPeopleService from '../services/people.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import Person from '../models/person.model';
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
        'MfElementSizeService',
        'ConfigService',
        'PeopleService'
    ];
    constructor(private $element: IAugmentedJQuery,
                $q: IQService,
                $scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                $translate: angular.translate.ITranslateService,
                private elementSizeService: ElementSizeService,
                configService,
                peopleService: IPeopleService) {
        super($q, $scope, $state, $stateParams, $translate, configService, peopleService);
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
        this.gotoState('search.table');
    }

    fetchData() {
        let searchResult = this.fetchSearchData();
        if (searchResult) {
            searchResult.then(this.onSearchResult.bind(this));
        }
    }

    private onSearchResult(searchResult: SearchResult): void {
        this.searchResult = new SearchResult({
            sizeExceeded: searchResult.sizeExceeded,
            searchResults: []
        });

        let self = this;

        searchResult.people.forEach(
            (person: Person) => {
                this.peopleService
                    .getPerson(person.userKey)
                    .then((person: Person) => {
                        // searchResult may be overwritten by ESC->[LETTER] typed in after a search
                        // has started but before all calls to peopleService.getPerson have resolved
                        if (self.searchResult) {
                            self.searchResult.people.push(person);
                        }
                    });
            },
            this);
    }
}
