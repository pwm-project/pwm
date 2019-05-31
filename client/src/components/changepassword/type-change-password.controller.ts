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


import {IHelpDeskService, ISuccessResponse} from '../../services/helpdesk.service';
import {ILogService, IQService, IScope, ITimeoutService, IWindowService} from 'angular';
import {IHelpDeskConfigService} from '../../services/helpdesk-config.service';
import {IChangePasswordSuccess} from './success-change-password.controller';
import {IPasswordService, IValidatePasswordData} from '../../services/password.service';
import IPwmService from '../../services/pwm.service';

require('./type-change-password.component.scss');

const EMPTY_MATCH_STATUS = 'EMPTY';
const IN_PROGRESS_MESSAGE_WAIT_MS = 5;

export default class TypeChangePasswordController {
    inputDebounce: number;
    maskPasswords: boolean;
    matchStatus: string;
    message: string;
    password1: string;
    password2: string;
    passwordAcceptable: boolean;
    passwordMasked: boolean;
    passwordSuggestions: string[];
    passwordUiMode: string;
    pendingValidation: boolean;
    showStrengthMeter: boolean;
    strength: number;

    static $inject = [
        '$log',
        '$q',
        '$scope',
        '$timeout',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'PasswordService',
        'personUserKey',
        'PwmService',
        'translateFilter'
    ];
    constructor(private $log: ILogService,
                private $q: IQService,
                private $scope: IScope,
                private $timeout: ITimeoutService,
                private configService: IHelpDeskConfigService,
                private HelpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private passwordService: IPasswordService,
                private personUserKey: string,
                private pwmService: IPwmService,
                private translateFilter: (id: string) => string) {
        this.inputDebounce = this.pwmService.ajaxTypingWait;
        this.matchStatus = EMPTY_MATCH_STATUS;
        this.message = translateFilter('Display_PasswordPrompt');
        this.password1 = '';
        this.password2 = '';
        this.passwordAcceptable = false;
        this.passwordSuggestions = [];
        for (let i = 0; i < 20; i++) {
            this.passwordSuggestions.push('');
        }
        this.pendingValidation = false;
        this.showStrengthMeter = HelpDeskService.showStrengthMeter;
        this.strength = 0;

        let promise = this.$q.all([
            this.configService.getPasswordUiMode(),
            this.configService.maskPasswordsEnabled()
        ]);
        promise.then((result) => {
            this.passwordUiMode = result[0];
            this.maskPasswords = result[1];
            this.passwordMasked = this.maskPasswords;
        });

        // Update dialog whenever a password field changes
        this.$scope.$watch('$ctrl.password1', (newValue, oldValue) => {
            if (newValue !== oldValue) {
                if (this.password2.length) {
                    this.password2 = '';
                }

                this.updateDialog();
            }
        });

        this.$scope.$watch('$ctrl.password2', (newValue, oldValue) => {
            if (newValue !== oldValue) {
                this.updateDialog();
            }
        });
    }

    chooseTypedPassword() {
        if (!this.passwordAcceptable) {
            return;
        }

        this.HelpDeskService.setPassword(this.personUserKey, false, this.password1)
            .then((result: ISuccessResponse) => {
                // Send the password and success message to the parent element via the close() method.
                let data: IChangePasswordSuccess = { password: this.password1, successMessage: result.successMessage };
                this.IasDialogService.close(data);
            });
    }

    // Use the autogenPasswords property to signify to the parent element that the operator clicked "Random Passwords"
    onClickRandomPasswords() {
        this.IasDialogService.close({ autogenPasswords: true });
    }

    togglePasswordMasked() {
        this.passwordMasked = !this.passwordMasked;
    }

    updateDialog() {
        // Since user may continue typing, don't process request if another is already in progress
        if (this.pendingValidation) {
            return;
        }
        this.pendingValidation = true;

        this.passwordService.validatePassword(this.password1, this.password2, this.personUserKey)
            .then(
                (data: IValidatePasswordData) => {
                    this.pendingValidation = false;
                    if (data.version !== 2) {
                        throw new Error('[ unexpected version string from server ]');
                    }

                    this.passwordAcceptable = data.passed && data.match === 'MATCH';
                    this.matchStatus = data.match;
                    this.message = data.message;

                    if (!this.password1) {
                        this.strength = 0;
                    }
                    if (data.strength < 20) {
                        this.strength = 1;
                    }
                    else if (data.strength < 45) {
                        this.strength = 2;
                    }
                    else if (data.strength < 70) {
                        this.strength = 3;
                    }
                    else if (data.strength < 100) {
                        this.strength = 4;
                    }
                    else {
                        this.strength = 5;
                    }
                },
                (result: any) => {
                    this.pendingValidation = false;
                    this.$log.error(result);
                    this.message = this.translateFilter('Display_CommunicationError');
                }
            );

        this.$timeout(() => {
            if (this.pendingValidation) {
                this.message = this.translateFilter('Display_CheckingPassword');
            }
        }, IN_PROGRESS_MESSAGE_WAIT_MS);
    }
}
