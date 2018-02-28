/*
 * Password Management Servlets (PWM)
  htt://www.pwm-project.org
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


import {Component} from '../component';
import {IButtonInfo, IHelpDeskService, ISuccessResponse} from '../services/helpdesk.service';
import {IScope, ui} from 'angular';
import {noop} from 'angular';
import {IHelpDeskConfigService, PASSWORD_UI_MODES} from '../services/helpdesk-config.service';
import {IPeopleService} from '../services/people.service';
import {IPerson} from '../models/person.model';
import {IChangePasswordSuccess} from '../changepassword/success-change-password.controller';

let autogenChangePasswordTemplateUrl = require('changepassword/autogen-change-password.component.html');
let helpdeskDetailDialogTemplateUrl = require('./helpdesk-detail-dialog.template.html');
let randomChangePasswordTemplateUrl = require('changepassword/random-change-password.component.html');
let successChangePasswordTemplateUrl = require('changepassword/success-change-password.component.html');
let typeChangePasswordTemplateUrl = require('changepassword/type-change-password.component.html');
let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');

const STATUS_WAIT = 'wait';
const STATUS_CONFIRM = 'confirm';
const STATUS_SUCCESS = 'success';

@Component({
    stylesheetUrl: require('helpdesk/helpdesk-detail.component.scss'),
    templateUrl: require('helpdesk/helpdesk-detail.component.html')
})
export default class HelpDeskDetailComponent {
    person: any;
    personCard: IPerson;
    photosEnabled: boolean;

    static $inject = [
        '$state',
        '$stateParams',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'PeopleService'
    ];

    constructor(private $state: ui.IStateService,
                private $stateParams: ui.IStateParamsService,
                private configService: IHelpDeskConfigService,
                private helpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        this.initialize();
    }

    buttonDisabled(buttonName: string): boolean {
        if (!this.person || !this.person.enabledButtons) {
            return false;
        }

        return (this.person.enabledButtons.indexOf(buttonName) === -1);
    }

    buttonVisible(buttonName: string): boolean {
        if (!this.person || !this.person.visibleButtons) {
            return false;
        }

        return (this.person.visibleButtons.indexOf(buttonName) !== -1);
    }

    changePassword(): void {
        this.configService.getPasswordUiMode()
            .then((passwordUiMode) => {
                if (passwordUiMode) {
                    if (passwordUiMode === PASSWORD_UI_MODES.TYPE) {
                        this.changePasswordType();
                    }
                    else if (passwordUiMode === PASSWORD_UI_MODES.AUTOGEN) {
                        this.changePasswordAutogen();
                    }
                    else if (passwordUiMode === PASSWORD_UI_MODES.BOTH) {
                        this.changePasswordType();
                    }
                    else if (passwordUiMode === PASSWORD_UI_MODES.RANDOM) {
                        this.changePasswordRandom();
                    }
                }
                else {
                    throw new Error('Unable to retrieve a valid password UI mode.');
                }
            });
    }

    changePasswordAutogen() {
        this.IasDialogService
            .open({
                controller: 'AutogenChangePasswordController as $ctrl',
                templateUrl: autogenChangePasswordTemplateUrl,
                locals: {
                    personUserKey: this.getUserKey()
                }
            })
            // If the password was changed, the promise resolves. IasDialogService passes the data intact.
            .then(this.changePasswordSuccess.bind(this), noop);
    }

    changePasswordClearResponses() {
        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'translateFilter',
                    function ($scope: IScope | any,
                              helpDeskService: IHelpDeskService,
                              translateFilter: (id: string) => string) {
                        $scope.status = STATUS_WAIT;
                        $scope.title = translateFilter('Button_ClearResponses');
                        helpDeskService.clearResponses(userKey).then((data: ISuccessResponse) => {
                            // TODO - error dialog?
                            $scope.status = STATUS_SUCCESS;
                            $scope.text = data.successMessage;
                        });
                    }
                ],
                templateUrl: helpdeskDetailDialogTemplateUrl
            });
    }

    changePasswordRandom() {
        this.IasDialogService
            .open({
                controller: 'RandomChangePasswordController as $ctrl',
                templateUrl: randomChangePasswordTemplateUrl,
                locals: {
                    personUsername: this.person.userDisplayName,
                    personUserKey: this.getUserKey()
                }
            })
            // If the password was changed, the promise resolves. IasDialogService passes the data intact.
            .then(this.changePasswordSuccess.bind(this), noop);
    }

    changePasswordSuccess(data: IChangePasswordSuccess) {
        this.IasDialogService
            .open({
                controller: 'SuccessChangePasswordController as $ctrl',
                templateUrl: successChangePasswordTemplateUrl,
                locals: {
                    changePasswordSuccessData: data,
                    personUsername: this.person.userDisplayName,
                    personUserKey: this.getUserKey()
                }
            })
            .then(this.changePasswordClearResponses.bind(this), noop);
    }

    changePasswordType() {
        this.IasDialogService
            .open({
                controller: 'TypeChangePasswordController as $ctrl',
                templateUrl: typeChangePasswordTemplateUrl,
                locals: {
                    personUsername: this.person.userDisplayName,
                    personUserKey: this.getUserKey()
                }
            })          // TODO: right data type?
            // If the operator clicked "Random Passwords" or the password was changed, the promise resolves.
            .then((data: IChangePasswordSuccess & { autogenPasswords: boolean }) => {
                // If the operator clicked "Random Passwords", data.autogenPasswords will be true
                if (data.autogenPasswords) {
                    this.changePasswordAutogen();
                }
                else {
                    this.changePasswordSuccess(data);   // IasDialogService passes the data intact.
                }
            }, noop);
    }

    clearOtpSecret(): void {
        if (this.buttonDisabled('clearOtpSecret')) {
            return;
        }

        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'translateFilter',
                    ($scope: IScope | any,
                     helpDeskService: IHelpDeskService,
                     translateFilter: (id: string) => string) => {
                        $scope.status = STATUS_CONFIRM;
                        $scope.title = translateFilter('Button_HelpdeskClearOtpSecret');
                        $scope.text = translateFilter('Confirm');
                        $scope.confirm = () => {
                            $scope.status = STATUS_WAIT;
                            helpDeskService.clearOtpSecret(userKey).then((data: ISuccessResponse) => {
                                // TODO - error dialog?
                                $scope.status = STATUS_SUCCESS;
                                $scope.text = data.successMessage;
                                this.refresh();
                            });
                        };
                    }
                ],
                templateUrl: helpdeskDetailDialogTemplateUrl
            });
    }

    clearResponses(): void {
        if (this.buttonDisabled('clearResponses')) {
            return;
        }

        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'translateFilter',
                    ($scope: IScope | any,
                     helpDeskService: IHelpDeskService,
                     translateFilter: (id: string) => string) => {
                        $scope.status = STATUS_CONFIRM;
                        $scope.title = translateFilter('Button_ClearResponses');
                        $scope.text = translateFilter('Confirm');
                        $scope.confirm = () => {
                            $scope.status = STATUS_WAIT;
                            helpDeskService.clearResponses(userKey).then((data: ISuccessResponse) => {
                                // TODO - error dialog?
                                $scope.status = STATUS_SUCCESS;
                                $scope.text = data.successMessage;
                                this.refresh();
                            });
                        };
                    }
                ],
                templateUrl: helpdeskDetailDialogTemplateUrl
            });
    }

    clickCustomButton(button: IButtonInfo): void {
        // Custom buttons are never disabled

        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'translateFilter',
                    function ($scope: IScope | any,
                              helpDeskService: IHelpDeskService,
                              translateFilter: (id: string) => string) {
                        $scope.status = STATUS_CONFIRM;
                        $scope.title = translateFilter('Button_Confirm') + ' ' + button.label;
                        $scope.text = button.description;
                        $scope.secondaryText = translateFilter('Confirm');
                        $scope.confirm = () => {
                            $scope.status = STATUS_WAIT;
                            helpDeskService.customAction(button.name, userKey).then((data: ISuccessResponse) => {
                                // TODO - error dialog? (note that this error dialog is slightly different)
                                $scope.status = STATUS_SUCCESS;
                                $scope.title = translateFilter('Title_Success');
                                $scope.secondaryText = null;
                                $scope.text = data.successMessage;
                            });
                        };
                    }
                ],
                templateUrl: helpdeskDetailDialogTemplateUrl
            });
    }

    deleteUser(): void {
        if (this.buttonDisabled('deleteUser')) {
            return;
        }

        let self = this;
        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'IasDialogService',
                    'translateFilter',
                    function ($scope: IScope | any,
                              helpDeskService: IHelpDeskService,
                              IasDialogService: any,
                              translateFilter: (id: string) => string) {
                        $scope.status = STATUS_CONFIRM;
                        $scope.title = translateFilter('Button_Confirm');
                        $scope.text = translateFilter('Confirm_DeleteUser');
                        $scope.confirm = () => {
                            $scope.status = STATUS_WAIT;
                            helpDeskService.deleteUser(userKey).then((data: ISuccessResponse) => {
                                // TODO - error dialog?
                                $scope.status = STATUS_SUCCESS;
                                $scope.title = translateFilter('Title_Success');
                                $scope.text = data.successMessage;
                                $scope.close = () => {
                                    IasDialogService.close();
                                    self.gotoSearch();
                                };
                            });
                        };
                    }
                ],
                templateUrl: helpdeskDetailDialogTemplateUrl
            });
    }

    getUserKey(): string {
        return this.$stateParams['personId'];
    }

    gotoSearch(): void {
        this.$state.go('search.cards');
    }

    initialize(): void {
        const personId = this.getUserKey();

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        }); // TODO: always necessary?

        this.peopleService.getPerson(personId).then((personCard: IPerson) => {
            this.personCard = personCard;
        });

        this.helpDeskService
            .getPerson(personId)
            .then((person: any) => {
                this.person = person;
            }, (error) => {
                // TODO: Handle error. NOOP for now will not assign person
            });
    }

    refresh(): void {
        this.person = null;
        this.initialize();
    }

    unlockUser(): void {
        if (this.buttonDisabled('unlock')) {
            return;
        }

        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'translateFilter',
                    ($scope: IScope | any,
                     helpDeskService: IHelpDeskService,
                     translateFilter: (id: string) => string) => {
                        $scope.status = STATUS_CONFIRM;
                        $scope.title = translateFilter('Button_Unlock');
                        $scope.text = translateFilter('Confirm');
                        $scope.confirm = () => {
                            $scope.status = STATUS_WAIT;
                            helpDeskService.unlockIntruder(userKey).then((data: ISuccessResponse) => {
                                // TODO - error dialog?
                                $scope.status = STATUS_SUCCESS;
                                $scope.text = data.successMessage;
                                this.refresh();
                            });
                        };
                    }
                ],
                templateUrl: helpdeskDetailDialogTemplateUrl
            });
    }

    verifyUser(): void {
        this.IasDialogService
            .open({
                controller: 'VerificationsDialogController as $ctrl',
                templateUrl: verificationsDialogTemplateUrl,
                locals: {
                    personUserKey: this.getUserKey(),
                    search: false
                }
            });
    }
}
