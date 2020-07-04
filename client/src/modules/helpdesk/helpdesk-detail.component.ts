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

import {Component} from '../../component';
import {IHelpDeskService, ISuccessResponse} from '../../services/helpdesk.service';
import {IScope, ui} from 'angular';
import {noop} from 'angular';
import {IActionButton, IHelpDeskConfigService, PASSWORD_UI_MODES} from '../../services/helpdesk-config.service';
import {IPerson} from '../../models/person.model';
import {IChangePasswordSuccess} from '../../components/changepassword/success-change-password.controller';
import LocalStorageService from '../../services/local-storage.service';

let autogenChangePasswordTemplateUrl =
    require('../../components/changepassword/autogen-change-password.component.html');
let helpdeskDetailDialogTemplateUrl = require('./helpdesk-detail-dialog.template.html');
let randomChangePasswordTemplateUrl = require('../../components/changepassword/random-change-password.component.html');
let successChangePasswordTemplateUrl =
    require('../../components/changepassword/success-change-password.component.html');
let typeChangePasswordTemplateUrl = require('../../components/changepassword/type-change-password.component.html');
let verificationsDialogTemplateUrl = require('./verifications-dialog.template.html');

const STATUS_WAIT = 'wait';
const STATUS_CONFIRM = 'confirm';
const STATUS_SUCCESS = 'success';
const PROFILE_TAB_NAME = 'profileTab';

@Component({
    stylesheetUrl: require('./helpdesk-detail.component.scss'),
    templateUrl: require('./helpdesk-detail.component.html')
})
export default class HelpDeskDetailComponent {
    customButtons: {[key: string]: IActionButton};
    person: any;
    personCard: IPerson;
    photosEnabled: boolean;
    searchViewLocalStorageKey: string;

    static $inject = [
        '$state',
        '$stateParams',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'IasToggleService',
        'LocalStorageService'
    ];

    constructor(private $state: ui.IStateService,
                private $stateParams: ui.IStateParamsService,
                private configService: IHelpDeskConfigService,
                private helpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private toggleService: { showComponent: (componentName: string) => null },
                private localStorageService: LocalStorageService) {
        this.searchViewLocalStorageKey = this.localStorageService.keys.HELPDESK_SEARCH_VIEW;
    }

    $onInit(): void {
        this.configService.getCustomButtons().then((customButtons: {[key: string]: IActionButton}) => {
            this.customButtons = customButtons;
        });

        this.configService.photosEnabled().then((photosEnabled: boolean) => {
            this.photosEnabled = photosEnabled;
        });

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
                    ($scope: IScope | any,
                     helpDeskService: IHelpDeskService,
                     translateFilter: (id: string) => string) => {
                        $scope.status = STATUS_WAIT;
                        $scope.title = translateFilter('Button_ClearResponses');
                        helpDeskService.clearResponses(userKey).then((data: ISuccessResponse) => {
                            // TODO - error dialog?
                            $scope.status = STATUS_SUCCESS;
                            $scope.text = data.successMessage;
                            this.refresh();
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
                    personUserKey: this.getUserKey()
                }
            })
            .then(this.changePasswordClearResponses.bind(this), this.refresh.bind(this));
    }

    changePasswordType() {
        this.IasDialogService
            .open({
                controller: 'TypeChangePasswordController as $ctrl',
                templateUrl: typeChangePasswordTemplateUrl,
                locals: {
                    personUserKey: this.getUserKey()
                }
            })
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

    clickCustomButton(button: IActionButton): void {
        // Custom buttons are never disabled

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
                        $scope.title = translateFilter('Button_Confirm') + ' ' + button.name;
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
                                this.refresh();
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

        let userKey = this.getUserKey();

        this.IasDialogService
            .open({
                controller: [
                    '$scope',
                    'HelpDeskService',
                    'IasDialogService',
                    'translateFilter',
                    ($scope: IScope | any,
                     helpDeskService: IHelpDeskService,
                     IasDialogService: any,
                     translateFilter: (id: string) => string) => {
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
                                    this.gotoSearch();
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
        let view = this.localStorageService.getItem(this.searchViewLocalStorageKey);
        if (view) {
            this.$state.go(view);
        }
        else {
            this.$state.go('search.cards');
        }
    }

    initialize(): void {
        const personId = this.getUserKey();

        this.helpDeskService.getPersonCard(personId).then((personCard: IPerson) => {
            this.personCard = personCard;
        });

        this.toggleService.showComponent(PROFILE_TAB_NAME);

        this.helpDeskService
            .getPerson(personId)
            .then((person: any) => {
                this.person = person;
            }, this.gotoSearch.bind(this));
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
                    showRequiredOnly: false
                }
            });
    }
}
