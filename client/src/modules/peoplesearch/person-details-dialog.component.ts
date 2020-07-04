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
import {IPeopleSearchConfigService, IPersonDetailsConfig} from '../../services/peoplesearch-config.service';
import { IPeopleService } from '../../services/people.service';
import {IAugmentedJQuery, ITimeoutService, noop} from 'angular';
import { IPerson } from '../../models/person.model';
import {IChangePasswordSuccess} from '../../components/changepassword/success-change-password.controller';

let orgchartExportTemplateUrl = require('./orgchart-export.component.html');
let orgchartEmailTemplateUrl = require('./orgchart-email.component.html');

@Component({
    stylesheetUrl: require('./person-details-dialog.component.scss'),
    templateUrl: require('./person-details-dialog.component.html')
})
export default class PersonDetailsDialogComponent {
    person: IPerson;
    photosEnabled: boolean;
    orgChartEnabled: boolean;
    exportEnabled: boolean;
    emailTeamEnabled: boolean;
    maxExportDepth: number;
    maxEmailDepth: number;

    static $inject = [
        '$element',
        '$state',
        '$stateParams',
        '$timeout',
        'ConfigService',
        'PeopleService',
        'IasDialogService'
    ];

    constructor(private $element: IAugmentedJQuery,
                private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private $timeout: ITimeoutService,
                private configService: IPeopleSearchConfigService,
                private peopleService: IPeopleService,
                private IasDialogService: any) {
    }

    $onInit(): void {
        const personId = this.$stateParams['personId'];

        this.configService.orgChartEnabled().then((orgChartEnabled: boolean) => {
            this.orgChartEnabled = orgChartEnabled;
        });

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        });

        this.configService.personDetailsConfig().then((personDetailsConfig: IPersonDetailsConfig) => {
            this.photosEnabled = personDetailsConfig.photosEnabled;
            this.orgChartEnabled = personDetailsConfig.orgChartEnabled;
            this.exportEnabled = personDetailsConfig.exportEnabled;
            this.emailTeamEnabled = personDetailsConfig.emailTeamEnabled;
            this.maxExportDepth = personDetailsConfig.maxExportDepth;
            this.maxEmailDepth = personDetailsConfig.maxEmailDepth;
        });

        this.peopleService
            .getPerson(personId)
            .then(
                (person: IPerson) => {
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

    getAvatarStyle(): any {
        if (!this.person || !this.person.photoURL || !this.photosEnabled) {
            return null;
        }

        return  { 'background-image': 'url(' + this.person.photoURL + ')' };
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

    beginExport() {
        this.IasDialogService
            .open({
                controller: 'OrgchartExportController as $ctrl',
                templateUrl: orgchartExportTemplateUrl,
                locals: {
                    peopleService: this.peopleService,
                    maxDepth: this.maxExportDepth,
                    personName: this.person.displayNames[0],
                    userKey: this.person.userKey
                }
            });
    }

    beginEmail() {
        this.IasDialogService
            .open({
                controller: 'OrgchartEmailController as $ctrl',
                templateUrl: orgchartEmailTemplateUrl,
                locals: {
                    peopleService: this.peopleService,
                    maxDepth: this.maxEmailDepth,
                    personName: this.person.displayNames[0],
                    userKey: this.person.userKey
                }
            });
    }
}
