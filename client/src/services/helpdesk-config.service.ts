/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
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


import { IHttpService, ILogService, IPromise, IQService } from 'angular';
import IPwmService from './pwm.service';
import PwmService from './pwm.service';
import {ConfigBaseService, IConfigService} from './base-config.service';

const COLUMN_CONFIG = 'helpdesk_search_columns';
const VERIFICATION_METHODS_CONFIG = 'verificationMethods';
const TOKEN_SEND_METHOD_CONFIG = 'helpdesk_setting_tokenSendMethod';
const TOKEN_VERIFICATION_METHOD = 'TOKEN';
const TOKEN_SMS_ONLY = 'SMSONLY';
const TOKEN_EMAIL_ONLY = 'EMAILONLY';
export const TOKEN_CHOICE = 'CHOICE_SMS_EMAIL';
const VERIFICATION_FORM_CONFIG = 'verificationForm';

export const VERIFICATION_METHOD_NAMES = {
    ATTRIBUTES: 'ATTRIBUTES',
    EMAIL: 'EMAIL',
    SMS: 'SMS',
    OTP: 'OTP'
};

export const VERIFICATION_METHOD_LABELS = {
    ATTRIBUTES: 'Button_Attributes',
    EMAIL: 'Button_Email',
    SMS: 'Button_SMS',
    OTP: 'Button_OTP'
};

interface IVerificationResponse {
    optional: string[];
    required: string[];
}

export type IVerificationMap = {name: string, label: string}[];

export interface IHelpDeskConfigService extends IConfigService {
    getTokenSendMethod(): IPromise<string>;
    getVerificationAttributes(): IPromise<IVerificationMap>;
    getVerificationMethods(): IPromise<IVerificationMap>;
    verificationsEnabled(): IPromise<boolean>;
}

export default class HelpDeskConfigService extends ConfigBaseService implements IConfigService, IHelpDeskConfigService {

    static $inject = ['$http', '$log', '$q', 'PwmService' ];
    constructor($http: IHttpService, $log: ILogService, $q: IQService, pwmService: IPwmService) {
        super($http, $log, $q, pwmService);
    }

    getColumnConfig(): IPromise<any> {
        return this.getValue(COLUMN_CONFIG);
    }

    getTokenSendMethod(): IPromise<string> {
        return this.getValue(TOKEN_SEND_METHOD_CONFIG);
    }

    getVerificationAttributes(): IPromise<IVerificationMap> {
        return this.getValue(VERIFICATION_FORM_CONFIG);
    }

    private getVerificationMethod(methodName): {name: string, label: string} {
        return {
            name: methodName,
            label: VERIFICATION_METHOD_LABELS[methodName]
        };
    }

    getVerificationMethods(): IPromise<IVerificationMap> {
        let promise = this.$q.all([
            this.getValue(VERIFICATION_METHODS_CONFIG),
            this.getTokenSendMethod()
        ]);

        return promise.then((result) => {
            let methods: IVerificationResponse = result[0];
            let tokenSendMethod: string = result[1];

            let verificationMethods: IVerificationMap = [];
            methods.required.forEach((method) => {
                if (method === TOKEN_VERIFICATION_METHOD) {
                    if (tokenSendMethod === TOKEN_EMAIL_ONLY || tokenSendMethod === TOKEN_CHOICE) {
                        verificationMethods.push(this.getVerificationMethod(VERIFICATION_METHOD_NAMES.EMAIL));
                    }

                    if (tokenSendMethod === TOKEN_SMS_ONLY || tokenSendMethod === TOKEN_CHOICE) {
                        verificationMethods.push(this.getVerificationMethod(VERIFICATION_METHOD_NAMES.SMS));
                    }
                }
                else {
                    verificationMethods.push(this.getVerificationMethod(method));
                }
            });

            return verificationMethods;
        });
    }

    verificationsEnabled(): IPromise<boolean> {
        return this.getValue(VERIFICATION_METHODS_CONFIG)
            .then((result: IVerificationResponse) => {
                return !!result.required.length;
            });
    }
}
