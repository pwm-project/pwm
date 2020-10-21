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

import * as angular from 'angular';
import {IPeopleService} from '../../services/people.service';

require('./orgchart-email.component.scss');

export default class OrgchartEmailController {
    depth = '1';
    fetchingTeamMembers = false;
    teamEmailList: string;

    static $inject = [
        '$window',
        'IasDialogService',
        'translateFilter',
        'peopleService',
        'maxDepth',
        'personName',
        'userKey'
    ];
    constructor(private $window: angular.IWindowService,
                private IasDialogService: any,
                private translateFilter: (id: string) => string,
                private peopleService: IPeopleService,
                private maxDepth: number,
                private personName: string,
                private userKey: string) {

        this.fetchEmailList();
    }

    emailOrgChart() {
        this.$window.location.href = `mailto:${this.teamEmailList}`;
        this.IasDialogService.close();
    }

    depthChanged() {
        this.fetchEmailList();
    }

    fetchEmailList() {
        this.fetchingTeamMembers = true;

        this.peopleService.getTeamEmails(this.userKey, +this.depth)
            .then((teamEmails: string[]) => {
                this.teamEmailList = teamEmails.toString();
            })
            .finally(() => {
                this.fetchingTeamMembers = false;
            });
    }
}
