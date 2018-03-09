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


import { IPromise, IQService } from 'angular';
import {ConfigBaseService} from './base-config.service.dev';
import {IConfigService} from './base-config.service';
import {IPeopleSearchConfigService} from './peoplesearch-config.service';


export default class ConfigService
                     extends ConfigBaseService
                     implements IConfigService, IPeopleSearchConfigService {
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
            telephoneNumber: 'Telephone'
        });
    }

    getOrgChartMaxParents(): IPromise<number> {
        return this.$q.resolve(50);
    }

    orgChartEnabled(): IPromise<boolean> {
        return this.$q.resolve(true);
    }

    orgChartShowChildCount(): IPromise<boolean> {
        return this.$q.resolve(true);
    }
}
