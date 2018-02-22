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


import {IPwmService} from './pwm.service';
import {ILogService, IPromise, IQService, IWindowService} from 'angular';
import LocalStorageService from './local-storage.service';
import ObjectService from './object.service';

const VERIFICATION_PROCESS_ACTIONS = {
    ATTRIBUTES: 'validateAttributes',
    EMAIL: 'verifyVerificationToken',
    SMS: 'verifyVerificationToken',
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
    getRandomPassword(userKey: string): IPromise<IRandomPasswordResponse>;
    getRecentVerifications(): IPromise<IRecentVerifications>;
    sendVerificationToken(userKey: string, choice: string): IPromise<IVerificationTokenResponse>;
    setPassword(userKey: string, random: boolean, password?: string): IPromise<ISuccessResponse>;
    unlockIntruder(userKey: string): IPromise<ISuccessResponse>;
    validateVerificationData(userKey: string, formData: any, tokenData: any): IPromise<IVerificationStatus>;
    showStrengthMeter: boolean;
}

export interface IButtonInfo {
    description: string;
    label: string;
    name: string;
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

export interface IVerificationStatus {
    passed: boolean;
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
                return this.$q.resolve(result.data);
            });
    }

    clearOtpSecret(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('clearOtpSecret');
        let data: any = { userKey: userKey };

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: ISuccessResponse) => {
                return this.$q.resolve(result);
            });
    }

    clearResponses(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('clearResponses');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: ISuccessResponse) => {
                return this.$q.resolve(result);
            });
    }

    customAction(actionName: string, userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('executeAction');
        url += `&name=${actionName}`;
        let data: any = { userKey: userKey };

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: ISuccessResponse) => {
                return this.$q.resolve(result);
            });
    }

    deleteUser(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('deleteUser');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: ISuccessResponse) => {
                return this.$q.resolve(result);
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
                return this.$q.resolve(result.data);
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
            .then((result: IRandomPasswordResponse) => {
                return this.$q.resolve(result);
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
                return this.$q.resolve(result.data.records);
            });
    }

    sendVerificationToken(userKey: string, choice: string): IPromise<IVerificationTokenResponse> {
        let url: string = this.pwmService.getServerUrl('sendVerificationToken');
        let data: any = { userKey: userKey };

        if (choice) {
            data.method = choice;
        }

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: IVerificationTokenResponse) => {
                return this.$q.resolve(result);
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
                return this.$q.resolve(result);
            });
    }

    unlockIntruder(userKey: string): IPromise<ISuccessResponse> {
        let url: string = this.pwmService.getServerUrl('unlockIntruder');
        url += `&userKey=${userKey}`;

        return this.pwmService
            .httpRequest(url, {})
            .then((result: ISuccessResponse) => {
                return this.$q.resolve(result);
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
                return this.$q.resolve(validationStatus);
            });
    }

    get showStrengthMeter(): boolean {
        if (this.PWM_GLOBAL) {
            return this.PWM_GLOBAL['setting-showStrengthMeter'] || DEFAULT_SHOW_STRENGTH_METER;
        }

        return DEFAULT_SHOW_STRENGTH_METER;
    }
}
