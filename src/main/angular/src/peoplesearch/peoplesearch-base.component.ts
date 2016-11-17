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


import { IPeopleService } from '../services/people.service';
import { IPromise, IScope } from 'angular';
import SearchResult from '../models/search-result.model';
import Person from '../models/person.model';

interface ISearchFunction {
    (query: string): IPromise<SearchResult>;
}

export default class PeopleSearchBaseComponent {
    query: string;
    searchFunction: ISearchFunction;
    searchMessage: string;
    searchResult: SearchResult;

    protected constructor(protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected peopleService: IPeopleService) {}

    gotoOrgchart(): void {
        this.$state.go('orgchart.index');
    }

    gotoState(state: string): void {
        this.$state.go(state, { query: this.query });
    }

    initialize(searchFunction: ISearchFunction): void {
        this.query = this.$stateParams['query'];
        this.searchFunction = searchFunction;

        const self = this;

        // Fetch data when query changes
        this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            if (newValue === oldValue) {
                return;
            }

            self.setSearchMessage(null);
            self.fetchData();
        });

        this.fetchData();
    }

    selectPerson(person: Person): void {
        this.$state.go('.details', { personId: person.userKey, query: this.query });
    }

    protected setSearchMessage(searchResult: SearchResult) {
        if (!searchResult) {
            this.searchMessage = null;
            return;
        }

        if (searchResult.sizeExceeded) {
            this.searchMessage = `Only showing ${searchResult.people.length} results`;
        }
        if (!searchResult.people.length) {
            this.searchMessage = 'No results';
        }
    }

    protected fetchData(): void {
        const self = this;

        if (!this.query) {
            self.searchResult = null;
            return;
        }

        this.searchFunction
            .call(this.peopleService, this.query)
            .then((searchResult: SearchResult) => {
                self.searchResult = searchResult;
                self.setSearchMessage(searchResult);
            });
    }
}
