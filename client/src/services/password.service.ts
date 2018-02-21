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


import {ILogService, IWindowService} from 'angular';
import {IPwmService} from './pwm.service';

export interface IPasswordService {
    validatePassword(password1: string, password2: string, userKey: string): IValidatePasswordResultsFunction;
}

export interface IValidatePasswordResultsFunction {
    onResult: (
        dataCallback: (IValidatePasswordData) => void,
        messageCallback: (message: string) => void
    ) => void;
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

    static $inject = [ '$log', '$window', 'pwmService', 'translateFilter' ];
    constructor(private $log: ILogService,
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

    validatePassword(password1: string, password2: string, userKey: string): IValidatePasswordResultsFunction {
        let data = { password1: password1, password2: password2, userDN: userKey };
        let processResultsFunction = null;
        let validationProps = {
            messageWorking: this.translateFilter('Display_CheckingPassword'),
            processResultsFunction: processResultsFunction,
            readDataFunction: () => data,
            serviceURL: this.pwmService.getServerUrl('checkPassword')
        };
        this.PWM_MAIN.pwmFormValidator(validationProps);

        return null;
    }
}
