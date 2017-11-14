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


import { IHttpService, ILogService, IPromise, IQService } from 'angular';
import IPwmService from './pwm.service';
import PwmService from './pwm.service';
import {ConfigBaseService, IConfigService} from './config-base.service';

const COLUMN_CONFIG = 'peoplesearch_search_columns';
const ORGCHART_ENABLED = 'peoplesearch_orgChartEnabled';

export interface IPeopleSearchConfigService extends IConfigService {
    orgChartEnabled(): IPromise<boolean>;
}

export default class PeopleSearchConfigService
                     extends ConfigBaseService
                     implements IConfigService, IPeopleSearchConfigService {

    static $inject = ['$http', '$log', '$q', 'PwmService' ];
    constructor($http: IHttpService, $log: ILogService, $q: IQService, pwmService: IPwmService) {
        super($http, $log, $q, pwmService);
    }

    getColumnConfig(): IPromise<any> {
        return this.getValue(COLUMN_CONFIG);
    }

    orgChartEnabled(): IPromise<boolean> {
        return this.getValue(ORGCHART_ENABLED)
            .then(null, () => { return this.$q.resolve(true); }); // On error use default
    }
}
