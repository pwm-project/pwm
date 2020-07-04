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

require('./verifications-dialog.component.scss');

import {ui, ITimeoutService} from 'angular';
import {
    IHelpDeskConfigService, IVerificationMap, TOKEN_CHOICE, VERIFICATION_METHOD_LABELS,
    VERIFICATION_METHOD_NAMES
} from '../../services/helpdesk-config.service';
import {
    IHelpDeskService,
    IVerificationOptions,
    IVerificationStatus,
    IVerificationTokenResponse
} from '../../services/helpdesk.service';
import ObjectService from '../../services/object.service';

const STATUS_FAILED = 'failed';
const STATUS_NONE = 'none';
const STATUS_PASSED = 'passed';
const STATUS_SELECT = 'select';
const STATUS_VERIFY = 'verify';
const STATUS_WAIT = 'wait';

export default class VerificationsDialogController {
    verificationOptions: IVerificationOptions;
    tokenDestinationID: string;
    sendingVerificationToken = false;
    verificationTokenSent = false;

    availableVerificationMethods: IVerificationMap;
    formData: any = {};
    inputs: { name: string, label: string }[];
    isDetailsView: boolean;
    status: string;
    tokenData: string;
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
        'showRequiredOnly'
    ];
    constructor(private $state: ui.IStateService,
                private $timeout: ITimeoutService,
                private configService: IHelpDeskConfigService,
                private helpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private objectService: ObjectService,
                private personUserKey: string,
                private showRequiredOnly: boolean) {

        this.isDetailsView = (this.$state.current.name === 'details');
        this.status = STATUS_WAIT;
        this.verificationStatus = STATUS_NONE;
        this.viewDetailsEnabled = false;

        this.helpDeskService
            .checkVerification(this.personUserKey)
            .then((response: IVerificationStatus) => {
                this.verificationOptions = response.verificationOptions;

                if (!this.isDetailsView && response.passed) {
                    // If we're not on the details page already, and verifications have been passed, then just go right
                    // to the details page:
                    this.gotoDetailsPage();
                }
                else {
                    this.status = STATUS_SELECT;
                    this.determineAvailableVerificationMethods();
                }
            })
            .catch((reason: any) => {
                alert(reason);

                this.status = STATUS_NONE;
                this.verificationStatus = STATUS_NONE;
                this.IasDialogService.close();
            });
    }

    determineAvailableVerificationMethods() {
        this.availableVerificationMethods = [];

        const methodNames: string[] = this.showRequiredOnly ?
            this.verificationOptions.verificationMethods.required :
            this.verificationOptions.verificationMethods.optional;

        if (methodNames) {
            for (let methodName of methodNames) {
                this.availableVerificationMethods.push({
                    name: methodName,
                    label: VERIFICATION_METHOD_LABELS[methodName]
                });
            }
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

    selectVerificationMethod(method: string) {
        this.verificationMethod = method;

        if (method === VERIFICATION_METHOD_NAMES.ATTRIBUTES) {
            this.configService.getVerificationAttributes()
                .then((response) => {
                    this.status = STATUS_VERIFY;
                    this.inputs = response;

                    // Need to initialize the formData values to empty strings, otherwise null values will be sent to
                    // the server
                    for (let i = 0; i < this.inputs.length; i++) {
                        this.formData[this.inputs[i].name] = '';
                    }
                });
        }
        else if (method === VERIFICATION_METHOD_NAMES.TOKEN) {
            this.status = STATUS_VERIFY;

            try {
                // Select the first destination in the list as default.
                this.tokenDestinationID = this.verificationOptions.tokenDestinations[0].id;
            } catch (error) {}
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
            this.objectService.assign(data, {
                tokenData: this.tokenData
            });
        }
        this.helpDeskService.validateVerificationData(this.personUserKey, data, this.verificationMethod)
            .then((response) => {
                if (response.passed) {
                    this.verificationStatus = STATUS_PASSED;
                }
                else {
                    this.verificationStatus = STATUS_FAILED;
                }
            })
            .catch((reason) => {
                this.verificationStatus = STATUS_FAILED;
            })
    }

    onTokenDestinationChanged() {
        this.verificationTokenSent = false;
    }

    sendVerificationTokenToDestination() {
        this.sendingVerificationToken = true;
        this.verificationTokenSent = false;

        this.helpDeskService.sendVerificationToken(this.personUserKey, this.tokenDestinationID)
            .then((response) => {
                this.verificationTokenSent = true;
                this.tokenData = (response as any).data.tokenData;
            })
            .catch((reason) => {
                this.verificationTokenSent = false;
                alert(reason);
            })
            .finally(() => {
                this.sendingVerificationToken = false;
            });
    }

    viewDetails() {
        if (this.verificationStatus === STATUS_PASSED) {
            this.gotoDetailsPage();
        }
    }
}
