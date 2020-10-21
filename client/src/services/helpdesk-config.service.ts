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


import { IHttpService, ILogService, IPromise, IQService } from 'angular';
import IPwmService from './pwm.service';
import PwmService from './pwm.service';
import {ConfigBaseService,
    IAdvancedSearchConfig,
    IConfigService,
    ADVANCED_SEARCH_ENABLED,
    ADVANCED_SEARCH_MAX_ATTRIBUTES,
    ADVANCED_SEARCH_ATTRIBUTES
} from './base-config.service';

const CLEAR_RESPONSES_CONFIG = 'clearResponses';
const CUSTOM_BUTTON_CONFIG = 'actions';
const MASK_PASSWORDS_CONFIG = 'maskPasswords';
const PASSWORD_UI_MODE_CONFIG = 'pwUiMode';
const TOKEN_SEND_METHOD_CONFIG = 'tokenSendMethod';
const TOKEN_VERIFICATION_METHOD = 'TOKEN';
const TOKEN_SMS_ONLY = 'SMSONLY';
const TOKEN_EMAIL_ONLY = 'EMAILONLY';
const VERIFICATION_FORM_CONFIG = 'verificationForm';
const VERIFICATION_METHODS_CONFIG = 'verificationMethods';
export const TOKEN_CHOICE = 'CHOICE_SMS_EMAIL';

export const PASSWORD_UI_MODES = {
    NONE: 'none',
    AUTOGEN: 'autogen',
    RANDOM: 'random',
    TYPE: 'type',
    BOTH: 'both'
};

export const VERIFICATION_METHOD_NAMES = {
    ATTRIBUTES: 'ATTRIBUTES',
    TOKEN: 'TOKEN',
    OTP: 'OTP'
};

export const VERIFICATION_METHOD_LABELS = {
    ATTRIBUTES: 'Button_Attributes',
    TOKEN: 'Button_TokenVerification',
    OTP: 'Button_OTP'
};

interface IVerificationResponse {
    optional: string[];
    required: string[];
}

export type IVerificationMap = {name: string, label: string}[];

export interface IActionButton {
    description: string;
    name: string;
}

export interface IHelpDeskConfigService extends IConfigService {
    getClearResponsesSetting(): IPromise<string>;
    getCustomButtons(): IPromise<{[key: string]: IActionButton}>;
    getPasswordUiMode(): IPromise<string>;
    getTokenSendMethod(): IPromise<string>;
    getVerificationAttributes(): IPromise<IVerificationMap>;
    maskPasswordsEnabled(): IPromise<boolean>;
    verificationsEnabled(): IPromise<boolean>;
    advancedSearchConfig(): IPromise<IAdvancedSearchConfig>;
}

export default class HelpDeskConfigService extends ConfigBaseService implements IConfigService, IHelpDeskConfigService {

    static $inject = ['$http', '$log', '$q', 'PwmService' ];
    constructor($http: IHttpService, $log: ILogService, $q: IQService, pwmService: IPwmService) {
        super($http, $log, $q, pwmService);
    }

    getClearResponsesSetting(): IPromise<string> {
        return this.getValue(CLEAR_RESPONSES_CONFIG);
    }

    getCustomButtons(): IPromise<{[key: string]: IActionButton}> {
        return this.getValue(CUSTOM_BUTTON_CONFIG);
    }

    getPasswordUiMode(): IPromise<string> {
        return this.getValue(PASSWORD_UI_MODE_CONFIG);
    }

    getTokenSendMethod(): IPromise<string> {
        return this.getValue(TOKEN_SEND_METHOD_CONFIG);
    }

    getVerificationAttributes(): IPromise<IVerificationMap> {
        return this.getValue(VERIFICATION_FORM_CONFIG);
    }

    maskPasswordsEnabled(): IPromise<boolean> {
        return this.getValue(MASK_PASSWORDS_CONFIG);
    }

    verificationsEnabled(): IPromise<boolean> {
        return this.getValue(VERIFICATION_METHODS_CONFIG)
            .then((result: IVerificationResponse) => {
                return !!result.required.length;
            });
    }

    advancedSearchConfig(): IPromise<IAdvancedSearchConfig> {
        return this.$q.all([
            this.getValue(ADVANCED_SEARCH_ENABLED),
            this.getValue(ADVANCED_SEARCH_MAX_ATTRIBUTES),
            this.getValue(ADVANCED_SEARCH_ATTRIBUTES)
        ]).then((result) => {
            return {
                enabled: result[0],
                maxRows: result[1],
                attributes: result[2]
            };
        });
    }
}
