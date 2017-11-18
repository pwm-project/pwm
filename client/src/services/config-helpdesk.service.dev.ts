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


import { IPromise, IQService } from 'angular';
import {ConfigBaseService} from './config-base.service.dev';
import {IConfigService} from './config-base.service';
import {IHelpDeskConfigService, IVerificationForm, IVerificationMethods} from './config-helpdesk.service';


export default class HelpDeskConfigService extends ConfigBaseService implements IConfigService, IHelpDeskConfigService {
    static $inject = [ '$q' ];
    constructor($q: IQService) {
        super($q);
    }

    getColumnConfig(): IPromise<any> {
        return this.$q.resolve({
            givenName: 'First Name',
            sn: 'Last Name',
            title: 'Title',
            mail: 'Email',
            telephoneNumber: 'Telephone',
            workforceId: 'Workforce ID'
        });
    }

    getVerificationForm(): angular.IPromise<IVerificationForm> {
        return this.$q.resolve([
            { name: 'workforceID', label: 'Workforce ID' },
            { name: 'mail', label: 'Email Address' }
        ]);
    }

    getVerificationMethods(): angular.IPromise<IVerificationMethods> {
        return this.$q.resolve({
            optional: [ 'ATTRIBUTES', 'TOKEN' ],
            required: [ 'ATTRIBUTES', 'TOKEN' ]
        });
    }
}
