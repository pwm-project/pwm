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
            deferred.resolve({ data: data} );
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
