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

const SIMULATED_RESPONSE_TIME = 100;
const STRENGTH_PER_PASSWORD_CHARACTER = 10;
const MAX_STRENGTH = 100;
const STRENGTH_REQUIRED = 40;

import {IPasswordService, IValidatePasswordData} from './password.service';
import {IPromise, IQService, ITimeoutService} from 'angular';

export default class PasswordService implements IPasswordService {

    static $inject = ['$q', '$timeout'];
    constructor(private $q: IQService, private $timeout: ITimeoutService) {
    }

    validatePassword(password1: string, password2: string, userKey: string): IPromise<IValidatePasswordData> {
        let strength = Math.min((password1.length * STRENGTH_PER_PASSWORD_CHARACTER), MAX_STRENGTH);
        let match = (password1 === password2);
        let message: string = null;
        let passed = (strength >= STRENGTH_REQUIRED);

        if (!password1) {
            message = 'Please type your new password';
        }
        if (!passed) {
            message = 'New password is too short';
        }
        else if (!password2) {
            message = 'Password meets requirements, please type confirmation password';
        }
        else if (!match) {
            message = 'Passwords do not match';
        }
        else {
            message = 'New password accepted, please click change password';
        }

        let matchStatus: string = null;
        if (!password1) {
            matchStatus = 'EMPTY';
        }
        else {
            matchStatus = match ? 'MATCH' : 'NO_MATCH';
        }

        let data = {
            version: 2,
            strength: strength,
            match: matchStatus,
            message: message,
            passed: passed,
            errorCode: 0
        };

        let self = this;

        let deferred = this.$q.defer();
        let deferredAbort = this.$q.defer();

        let timeoutPromise = this.$timeout(() => {
            deferred.resolve(data);
        }, SIMULATED_RESPONSE_TIME);

        // To simulate an abortable promise, edit SIMULATED_RESPONSE_TIME
        deferred.promise['_httpTimeout'] = deferredAbort;
        deferredAbort.promise.then(() => {
            self.$timeout.cancel(timeoutPromise);
            deferred.resolve();
        });

        return deferred.promise as IPromise<IValidatePasswordData>;
    }
}
