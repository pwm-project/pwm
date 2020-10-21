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


import {ILogService, IPromise, IQService, ITimeoutService, IWindowService} from 'angular';
import {IPwmService} from './pwm.service';

export interface IPasswordService {
    validatePassword(password1: string, password2: string, userKey: string): IPromise<IValidatePasswordData>;
}

export interface IValidatePasswordData {
    version: number;
    strength: number;
    match: string;
    message: string;
    passed: boolean;
    errorCode: number;
}

export default class PasswordService implements IPasswordService {
    PWM_MAIN: any;

    static $inject = ['$log', '$q', '$timeout', '$window', 'PwmService', 'translateFilter'];

    constructor(private $log: ILogService,
                private $q: IQService,
                private $timeout: ITimeoutService,
                private $window: IWindowService,
                private pwmService: IPwmService,
                private translateFilter: (id: string) => string) {
        if ($window['PWM_MAIN']) {
            this.PWM_MAIN = $window['PWM_MAIN'];
        }
        else {
            this.$log.warn('PWM_MAIN is not defined on window');
        }
    }

    validatePassword(password1: string, password2: string, userKey: string): IPromise<IValidatePasswordData> {
        let data = {
            password1: password1,
            password2: password2,
            username: userKey
        };
        let url: string = this.pwmService.getServerUrl('checkPassword');

        return this.pwmService
            .httpRequest(url, {data: data})
            .then((result: { data: IValidatePasswordData }) => {
                return result.data;
            });
    }
}
