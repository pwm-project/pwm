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
import { isArray, isString, IPromise, IQService, IScope } from 'angular';
import { IPeopleService } from '../services/people.service';
import Person from '../models/person.model';
import OrgChartData from '../models/orgchart-data.model';

@Component({
    stylesheetUrl: require('peoplesearch/orgchart-search.component.scss'),
    templateUrl: require('peoplesearch/orgchart-search.component.html')
})
export default class OrgChartSearchComponent {
    directReports: Person[];
    managementChain: Person[];
    person: Person;
    query: string;

    static $inject = [ '$q', '$scope', '$state', '$stateParams', 'PeopleService' ];
    constructor(private $q: IQService,
                private $scope: IScope,
                private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        const self = this;

        // Read query from state parameters
        const queryParameter = this.$stateParams['query'];
        // If multiple query parameters are defined, use the first one
        if (isArray(queryParameter)) {
            this.query = queryParameter[0].trim();
        }
        else if (isString(queryParameter)) {
            this.query = queryParameter.trim();
        }

        let personId: string = this.$stateParams['personId'];

        this.fetchOrgChartData(personId)
            .then((orgChartData: OrgChartData) => {
                // Override personId in case it was undefined
                personId = orgChartData.self.userKey;

                self.$q.all({
                    directReports: self.peopleService.getDirectReports(personId),
                    managementChain: self.peopleService.getManagementChain(personId),
                    person: self.peopleService.getPerson(personId)
                })
                .then((data) => {
                    self.$scope.$evalAsync(() => {
                        self.directReports = data['directReports'];
                        self.managementChain = data['managementChain'];
                        self.person = data['person'];
                    });
                })
                .catch(() => {
                    // TODO: error handling
                });
            });
    }

    autoCompleteSearch(query: string): IPromise<Person[]> {
        return this.peopleService.autoComplete(query);
    }

    gotoSearchState(state: string) {
        this.$state.go(state, { query: this.query });
    }

    onAutoCompleteItemSelected(person: Person): void {
        this.$state.go('orgchart.search', { personId: person.userKey, query: null });
    }

    onSearchTextChange(value: string): void {
        this.query = value;
    }

    private fetchOrgChartData(personId): IPromise<OrgChartData> {
        return this.peopleService.getOrgChartData(personId);
    }
}
