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
import {IHelpDeskService} from '../services/helpdesk.service';
import {ui} from '@types/angular';
import {IActionButtons, IHelpDeskConfigService} from '../services/helpdesk-config.service';
import DialogService from '../ux/ias-dialog.service';

let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');

@Component({
    stylesheetUrl: require('helpdesk/helpdesk-detail.component.scss'),
    templateUrl: require('helpdesk/helpdesk-detail.component.html')
})
export default class HelpDeskDetailComponent {
    actionButtons: IActionButtons;
    photosEnabled: boolean;
    person: any;

    static $inject = [
        '$state',
        '$stateParams',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService'
    ];
    constructor(private $state: ui.IStateService,
                private $stateParams: ui.IStateParamsService,
                private configService: IHelpDeskConfigService,
                private helpDeskService: IHelpDeskService,
                private IasDialogService: DialogService) {
    }

    $onInit(): void {
        this.initialize();
    }

    changePassword(): void {
        alert('Change password dialog');
    }

    gotoSearch(): void {
        this.$state.go('search');
    }

    initialize(): void {
        const personId = this.$stateParams['personId'];

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        }); // TODO: always necessary?

        this.helpDeskService
            .getPerson(personId)
            .then((person: any) => {
                this.person = person;
            }, (error) => {
                // TODO: Handle error. NOOP for now will not assign person
            });

        this.configService
            .getActionButtons()
            .then((actionButtons: IActionButtons) => {
                this.actionButtons = actionButtons;
            });
    }

    refresh(): void {
        this.initialize();
    }

    unlockUser(): void {
        if (this.person.accountEnabled) {
            return;
        }

        alert('Unlock user dialog');
    }

    verifyUser(): void {
        this.IasDialogService
            .open({
                controller: 'VerificationsDialogController as $ctrl',
                templateUrl: verificationsDialogTemplateUrl,
                locals: {
                    personUserKey: this.$stateParams['personId'],
                    search: false
                }
            });
    }
}
