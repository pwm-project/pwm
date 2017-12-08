/*
 * Password Management Servlets (PWM)
  htt://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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


import {Component} from '../component';
import {IConfigService} from '../services/base-config.service';
import {IPeopleService} from '../services/people.service';
import {IPerson} from '../models/person.model';
import {IHelpDeskService} from '../services/helpdesk.service';

@Component({
    stylesheetUrl: require('helpdesk/helpdesk-detail.component.scss'),
    templateUrl: require('helpdesk/helpdesk-detail.component.html')
})
export default class HelpDeskDetailComponent {
    photosEnabled: boolean;

    person: IPerson;

    static $inject = [ '$stateParams', 'ConfigService', 'HelpDeskService', 'PeopleService' ];
    constructor(private $stateParams: angular.ui.IStateParamsService,
                private configService: IConfigService,
                private helpDeskService: IHelpDeskService,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        const personId = this.$stateParams['personId'];

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        }); // TODO: always necessary?

        this.helpDeskService
            .getPerson(personId)
            .then((person: any) => {
                console.log(person);
            }, (error) => {
                // TODO: Handle error. NOOP for now will not assign person
            });
    }
}
