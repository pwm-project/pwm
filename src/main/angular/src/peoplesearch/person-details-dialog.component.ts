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
import { IConfigService } from '../services/config.service';
import { IPeopleService } from '../services/people.service';
import Person from '../models/person.model';
import { IAugmentedJQuery, ITimeoutService } from 'angular';

@Component({
    stylesheetUrl: require('peoplesearch/person-details-dialog.component.scss'),
    templateUrl: require('peoplesearch/person-details-dialog.component.html')
})
export default class PersonDetailsDialogComponent {
    person: Person;
    photosEnabled: boolean;
    orgChartEnabled: boolean;

    static $inject = [ '$element', '$state', '$stateParams', '$timeout', 'ConfigService', 'PeopleService' ];

    constructor(private $element: IAugmentedJQuery,
                private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private $timeout: ITimeoutService,
                private configService: IConfigService,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        const personId = this.$stateParams['personId'];

        this.configService.orgChartEnabled().then((orgChartEnabled: boolean) => {
            this.orgChartEnabled = orgChartEnabled;
        });

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        });

        this.peopleService
            .getPerson(personId)
            .then(
                (person: Person) => {
                    this.person = person;
                },
                (error) => {
                    // TODO: Handle error. NOOP for now will not assign person
                });
    }

    $postLink() {
        const self = this;
        this.$timeout(() => {
            self.$element.find('button')[0].focus();
        }, 100);
    }

    closeDialog(): void {
        this.$state.go('^', { query: this.$stateParams['query'] });
    }

    gotoOrgChart(): void {
        this.$state.go('orgchart.search', { personId: this.person.userKey });
    }

    getPersonDetailsUrl(personId: string): string {
        return this.$state.href('.', { personId: personId }, { inherit: true, });
    }

    searchText(text: string): void {
        this.$state.go('search.table', { query: text });
    }
}
