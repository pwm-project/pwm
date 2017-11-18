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


import {IPwmService} from './pwm.service';
import {ILogService, IPromise, IQService, IWindowService} from 'angular';
import LocalStorageService from './local-storage.service';

export interface IHelpDeskService {
    checkVerification(userKey: string): IPromise<IVerificationStatus>;
}

export interface IVerificationStatus {
    passed: boolean;
}

export default class HelpDeskService implements IHelpDeskService {
    PWM_GLOBAL: any;

    static $inject = [ '$log', '$q', 'LocalStorageService', 'PwmService', '$window' ];
    constructor(private $log: ILogService,
                private $q: IQService,
                private localStorageService: LocalStorageService,
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
        let url: string =  this.pwmService.getServerUrl('checkVerification');
        let data = {
            userKey: userKey,
            verificationState: this.localStorageService.getItem(this.localStorageService.keys.VERIFICATION_STATE)
        };

        return this.pwmService
            .httpRequest(url, { data: data })
            .then((result: IVerificationStatus) => {
                return this.$q.resolve(result);
            });
    }


}
