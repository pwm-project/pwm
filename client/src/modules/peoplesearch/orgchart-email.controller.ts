/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
