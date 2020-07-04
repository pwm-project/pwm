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


import {IPwmService} from './pwm.service';
import {ILogService, IPromise, IQService, IWindowService} from 'angular';
import LocalStorageService from './local-storage.service';
import ObjectService from './object.service';
import SearchResult from '../models/search-result.model';
import {IQuery} from './people.service';

const VERIFICATION_PROCESS_ACTIONS = {
    ATTRIBUTES: 'validateAttributes',
    TOKEN: 'verifyVerificationToken',
    OTP: 'validateOtpCode'
};

const DEFAULT_SHOW_STRENGTH_METER = false;

export interface IHelpDeskService {
    checkVerification(userKey: string): IPromise<IVerificationStatus>;
    clearOtpSecret(userKey: string): IPromise<ISuccessResponse>;
    clearResponses(userKey: string): IPromise<ISuccessResponse>;
    customAction(actionName: string, userKey: string): IPromise<ISuccessResponse>;
    deleteUser(userKey: string): IPromise<ISuccessResponse>;
    getPerson(userKey: string): IPromise<any>;
    getPersonCard(userKey: string): IPromise<any>;
    getRandomPassword(userKey: string): IPromise<IRandomPasswordResponse>;
    getRecentVerifications(): IPromise<IRecentVerifications>;
    search(query: string): IPromise<SearchResult>;
    advancedSearch(queries: IQuery[]): IPromise<SearchResult>;
    sendVerificationToken(userKey: string, choice: string): IPromise<IVerificationTokenResponse>;
    setPassword(userKey: string, random: boolean, password?: string): IPromise<ISuccessResponse>;
    unlockIntruder(userKey: string): IPromise<ISuccessResponse>;
    validateVerificationData(userKey: string, formData: any, method: any): IPromise<IVerificationStatus>;
    showStrengthMeter: boolean;
}

export type IRecentVerifications = IRecentVerification[];

type IRecentVerification = {
    profile: string,
    username: string,
    timestamp: string,
    method: string
};

export interface IRandomPasswordResponse {
    password: string;
}

export interface ISuccessResponse {
    successMessage: string;
}

interface IValidationStatus extends IVerificationStatus {
    verificationState: string;
}

export interface IVerificationOptions {
    verificationMethods: {
        optional: string[];
        required: string[];
    },
    verificationForm: [{
        name: string;
        label: string;
    }],
    tokenDestinations: [{
        id: string;
        display: string;
        type: string;
    }]
}

export interface IVerificationStatus {
    passed: boolean;
    verificationOptions: IVerificationOptions;
}

export interface IVerificationTokenResponse {
    destination: string;
}

export default class HelpDeskService implements IHelpDeskService {
    PWM_GLOBAL: any;

    static $inject = [ '$log', '$q', 'LocalStorageService', 'ObjectService', 'PwmService', '$window' ];
    constructor(private $log: ILogService,
                private $q: IQService,
                private localStorageService: LocalStorageService,
                private objectService: ObjectService,
                private pwmService: IPwmService,
                $window: IWindowService) {
        if ($window['PWM_GLOBAL']) {
            this.PWM_GLOBAL = $window['PWM_GLOBAL'];
        }
        else {
            this.$log.warn('PWM_GLOBAL is not defined on window');
        }
    }

    checkVerification(userKey: string): IPromise<IVerificationStatus> {
        let url: string = this.pwmService.getServerUrl('checkVerification');
        let data = {
            userKey: userKey,
            verificationState: undefined
        };

        const verificationState = this.localStorageService.getItem(this.localStorageService.keys.VERIFICATION_STATE);
        if (verificationState != null) {
            data.verificationState = verificationState;
        }

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: any) => {
                return result.data;
            });
    }

    clearOtpSecret(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('clearOtpSecret');
        let data: any = { userKey: userKey };

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: ISuccessResponse) => {
                return result;
            });
    }

    clearResponses(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('clearResponses');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: ISuccessResponse) => {
                return result;
            });
    }

    customAction(actionName: string, userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('executeAction');
        url += `&name=${actionName}`;
        let data: any = { userKey: userKey };

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: ISuccessResponse) => {
                return result;
            });
    }

    deleteUser(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('deleteUser');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: ISuccessResponse) => {
                return result;
            });
    }

    getPerson(userKey: string): IPromise<any> {
        let url: string = this.pwmService.getServerUrl('detail');
        url += `&userKey=${userKey}`;

        const verificationState = this.localStorageService.getItem(this.localStorageService.keys.VERIFICATION_STATE);
        if (verificationState != null) {
            url += `&verificationState=${verificationState}`;
        }

        return this.pwmService
            .httpRequest(url, {})
            .then((result: any) => {
                return result.data;
            });
    }

    getPersonCard(userKey: string): IPromise<any> {
        let url = this.pwmService.getServerUrl('card');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: any) => {
                return result.data;
            });
    }

    getRandomPassword(userKey: string): IPromise<IRandomPasswordResponse> {
        let url: string = this.pwmService.getServerUrl('randomPassword');
        let data = {
            username: userKey,
            strength: 0
        };
        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: { data: IRandomPasswordResponse }) => {
                return result.data;
            });
    }

    getRecentVerifications(): IPromise<IRecentVerifications> {
        let url: string = this.pwmService.getServerUrl('showVerifications');
        const data = {
            verificationState: undefined
        };

        const verificationState = this.localStorageService.getItem(this.localStorageService.keys.VERIFICATION_STATE);
        if (verificationState != null) {
            data.verificationState = verificationState;
        }

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: any) => {
                return result.data.records;
            });
    }

    search(query: string): IPromise<SearchResult> {
        let formID: string = encodeURIComponent('&pwmFormID=' + this.PWM_GLOBAL['pwmFormID']);
        let url: string = this.pwmService.getServerUrl('search')
            + '&pwmFormID=' + this.PWM_GLOBAL['pwmFormID'];

        let data = {
            mode: 'simple',
            username: query,
            pwmFormID: formID
        };
        return this.pwmService
            .httpRequest(url, {
                data: data,
                preventCache: true
            })
            .then((result: any) => {
                let receivedData: any = result.data;
                let searchResult: SearchResult = new SearchResult(receivedData);
                return searchResult;
            });
    }

    advancedSearch(queries: IQuery[]): IPromise<SearchResult> {
        let formID: string = encodeURIComponent('&pwmFormID=' + this.PWM_GLOBAL['pwmFormID']);
        let url: string = this.pwmService.getServerUrl('search')
            + '&pwmFormID=' + this.PWM_GLOBAL['pwmFormID'];

        let data = {
            mode: 'advanced',
            pwmFormID: formID,
            searchValues: queries
        };
        return this.pwmService
            .httpRequest(url, {
                data: data,
                preventCache: true
            })
            .then((result: any) => {
                let receivedData: any = result.data;
                let searchResult: SearchResult = new SearchResult(receivedData);
                return searchResult;
            });
    }

    sendVerificationToken(userKey: string, destinationID: string): IPromise<IVerificationTokenResponse> {
        let url: string = this.pwmService.getServerUrl('sendVerificationToken');
        let data: any = {
            userKey: userKey,
            id: destinationID
        };

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: IVerificationTokenResponse) => {
                return result;
            });
    }

    setPassword(userKey: string, random: boolean, password?: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('setPassword');
        let data: any = { username: userKey };
        if (random) {
            data.random = true;
        }
        else {
            data.password = password;
        }

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: ISuccessResponse) => {
                return result;
            });
    }

    unlockIntruder(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('unlockIntruder');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: ISuccessResponse) => {
                return result;
            });
    }

    validateVerificationData(userKey: string, data: any, method: string): IPromise<IVerificationStatus> {
        let processAction = VERIFICATION_PROCESS_ACTIONS[method];
        let url: string = this.pwmService.getServerUrl(processAction);
        let content = {
            userKey: userKey,
            verificationState: undefined
        };

        const verificationState = this.localStorageService.getItem(this.localStorageService.keys.VERIFICATION_STATE);
        if (verificationState != null) {
            content.verificationState = verificationState;
        }

        this.objectService.assign(data, content);

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: any) => {
                const validationStatus: IValidationStatus = result.data;

                this.localStorageService.setItem(
                    this.localStorageService.keys.VERIFICATION_STATE,
                    validationStatus.verificationState
                );
                return validationStatus;
            });
    }

    get showStrengthMeter(): boolean {
        if (this.PWM_GLOBAL) {
            return this.PWM_GLOBAL['setting-showStrengthMeter'] || DEFAULT_SHOW_STRENGTH_METER;
        }

        return DEFAULT_SHOW_STRENGTH_METER;
    }
}
