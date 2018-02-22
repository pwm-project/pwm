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


import {ui, ITimeoutService} from 'angular';
import {
    IHelpDeskConfigService, IVerificationMap, TOKEN_CHOICE,
    VERIFICATION_METHOD_NAMES
} from '../services/helpdesk-config.service';
import {IHelpDeskService, IVerificationTokenResponse} from '../services/helpdesk.service';
import {IPerson} from '../models/person.model';
import ObjectService from '../services/object.service';

const STATUS_FAILED = 'failed';
const STATUS_NONE = 'none';
const STATUS_PASSED = 'passed';
const STATUS_SELECT = 'select';
const STATUS_VERIFY = 'verify';
const STATUS_WAIT = 'wait';

export default class VerificationsDialogController {
    availableVerificationMethods: IVerificationMap;
    formData: any = {};
    inputs: { name: string, label: string }[];
    isDetailsView: boolean;
    status: string;
    tokenData: IVerificationTokenResponse;
    viewDetailsEnabled: boolean;
    verificationMethod: string;
    verificationStatus: string;

    static $inject = [
        '$state',
        '$timeout',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'ObjectService',
        'personUserKey',
    ];
    constructor(private $state: ui.IStateService,
                private $timeout: ITimeoutService,
                private configService: IHelpDeskConfigService,
                private helpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private objectService: ObjectService,
                private personUserKey: string,
                private search: boolean) {
        this.isDetailsView = (this.$state.current.name === 'details');
        this.status = STATUS_WAIT;
        this.verificationStatus = STATUS_NONE;
        this.viewDetailsEnabled = false;

        if (this.isDetailsView) {
            this.loadVerificationOptions();
        }
        else {
            this.helpDeskService
                .checkVerification(this.personUserKey)
                .then((response) => {
                    if (response.passed) {
                        this.gotoDetailsPage();
                    }
                    else {
                        this.loadVerificationOptions();
                    }
                });
        }
    }

    clickOkButton() {
        if (this.verificationStatus === STATUS_PASSED) {
            this.IasDialogService.close();
        }
    }

    private gotoDetailsPage() {
        this.$timeout(() => {
            this.IasDialogService.close();
            this.$state.go('details', {personId: this.personUserKey});
        });
    }

    private loadVerificationOptions() {
        this.configService
            .getVerificationMethods({includeOptional: this.isDetailsView})
            .then((methods) => {
                this.status = STATUS_SELECT;
                this.availableVerificationMethods = methods;
            });
    }

    selectVerificationMethod(method: string) {
        this.verificationMethod = method;

        if (method === VERIFICATION_METHOD_NAMES.ATTRIBUTES) {
            this.configService.getVerificationAttributes()
                .then((response) => {
                    this.status = STATUS_VERIFY;
                    this.inputs = response;
                });
        }
        else if (method === VERIFICATION_METHOD_NAMES.SMS || method === VERIFICATION_METHOD_NAMES.EMAIL) {
            this.status = STATUS_WAIT;
            this.configService.getTokenSendMethod()
                .then((tokenSendMethod) => {
                    let choice = (tokenSendMethod === TOKEN_CHOICE) ? method : null;
                    return this.helpDeskService.sendVerificationToken(this.personUserKey, choice);
                })
                .then((response) => {
                    this.status = STATUS_VERIFY;
                    this.tokenData = response;
                });
        }
        else if (method === VERIFICATION_METHOD_NAMES.OTP) {
            this.status = STATUS_VERIFY;
        }
    }

    sendVerificationData() {
        this.verificationStatus = STATUS_WAIT;
        let data = {};
        this.objectService.assign(data, this.formData);
        if (this.tokenData) {
            this.objectService.assign(data, this.tokenData);
        }
        this.helpDeskService.validateVerificationData(this.personUserKey, data, this.verificationMethod)
            .then((response) => {
                if (response.passed) {
                    this.verificationStatus = STATUS_PASSED;
                }
                else {
                    this.verificationStatus = STATUS_FAILED;
                }
            });
    }

    viewDetails() {
        if (this.verificationStatus === STATUS_PASSED) {
            this.gotoDetailsPage();
        }
    }
}
