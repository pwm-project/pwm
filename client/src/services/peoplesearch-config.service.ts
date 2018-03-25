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


import { IHttpService, ILogService, IPromise, IQService } from 'angular';
import IPwmService from './pwm.service';
import PwmService from './pwm.service';
import {ConfigBaseService, IConfigService} from './base-config.service';

const COLUMN_CONFIG = 'searchColumns';
const ORGCHART_ENABLED = 'orgChartEnabled';
const ORGCHART_MAX_PARENTS = 'orgChartMaxParents';
const ORGCHART_SHOW_CHILD_COUNT = 'orgChartShowChildCount';

export interface IPeopleSearchConfigService extends IConfigService {
    getOrgChartMaxParents(): IPromise<number>;
    orgChartEnabled(): IPromise<boolean>;
    orgChartShowChildCount(): IPromise<boolean>;
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

    getOrgChartMaxParents(): IPromise<number> {
        return this.getValue(ORGCHART_MAX_PARENTS);
    }

    orgChartEnabled(): IPromise<boolean> {
        return this.getValue(ORGCHART_ENABLED)
            .then(null, () => { return this.$q.resolve(true); }); // On error use default
    }

    orgChartShowChildCount(): IPromise<boolean> {
        return this.getValue(ORGCHART_SHOW_CHILD_COUNT);
    }
}
