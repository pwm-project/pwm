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

import {
    IHelpDeskService, IRecentVerifications, IVerificationStatus,
    IVerificationTokenResponse
} from './helpdesk.service';
import {IPromise, IQService, IWindowService} from 'angular';

export default class HelpDeskService implements IHelpDeskService {
    PWM_GLOBAL: any;

    static $inject = [ '$q', '$window' ];
    constructor(private $q: IQService, private $window: IWindowService) {
    }

    checkVerification(userKey: string): IPromise<IVerificationStatus> {
        return this.$q.resolve({ passed: false });
    }

    getPerson(userKey: string): IPromise<any> {
        return null;
    }

    getRecentVerifications(): IPromise<IRecentVerifications> {
        return this.$q.resolve([
            {
                timestamp: '2017-12-06T23:19:07Z',
                profile: 'default',
                username: 'aastin',
                method: 'Personal Data'
            },
            {
                timestamp: '2017-12-03T22:11:07Z',
                profile: 'default',
                username: 'bjroach',
                method: 'Personal Data'
            },
            {
                timestamp: '2017-12-02T13:09:07Z',
                profile: 'default',
                username: 'rrhoads',
                method: 'Personal Data'
            }
        ]);
    }

    sendVerificationToken(userKey: string, choice: string): IPromise<IVerificationTokenResponse> {
        return this.$q.resolve({ destination: 'bcarrolj@paypal.com' });
    }

    validateVerificationData(userKey: string, data: any, method: string): IPromise<IVerificationStatus> {
        return this.$q.resolve({ passed: true });
    }
}
