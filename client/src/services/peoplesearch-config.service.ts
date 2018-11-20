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


import {IDeferred, IHttpService, ILogService, IPromise, IQService} from 'angular';
import IPwmService from './pwm.service';
import PwmService from './pwm.service';
import {
    ConfigBaseService,
    IConfigService,
    IAdvancedSearchConfig,
    ADVANCED_SEARCH_ENABLED,
    ADVANCED_SEARCH_MAX_ATTRIBUTES,
    ADVANCED_SEARCH_ATTRIBUTES, PHOTO_ENABLED
} from './base-config.service';

const ORGCHART_ENABLED = 'orgChartEnabled';
const ORGCHART_MAX_PARENTS = 'orgChartMaxParents';
const ORGCHART_SHOW_CHILD_COUNT = 'orgChartShowChildCount';
const EXPORT_ENABLED = 'enableExport';
const EXPORT_MAX_DEPTH = 'exportMaxDepth';
const MAILTO_ENABLED = 'enableMailtoLinks';
const MAILTO_MAX_DEPTH = 'mailtoLinkMaxDepth';

export interface IPeopleSearchConfigService extends IConfigService {
    getOrgChartMaxParents(): IPromise<number>;
    orgChartEnabled(): IPromise<boolean>;
    orgChartShowChildCount(): IPromise<boolean>;
    advancedSearchConfig(): IPromise<IAdvancedSearchConfig>;
    personDetailsConfig(): IPromise<IPersonDetailsConfig>;
}

export interface IPersonDetailsConfig {
    photosEnabled: boolean;
    orgChartEnabled: boolean;
    exportEnabled: boolean;
    emailTeamEnabled: boolean;
    maxExportDepth: number;
    maxEmailDepth: number;
}

export default class PeopleSearchConfigService
                     extends ConfigBaseService
                     implements IConfigService, IPeopleSearchConfigService {

    static $inject = ['$http', '$log', '$q', 'PwmService' ];
    constructor($http: IHttpService, $log: ILogService, $q: IQService, pwmService: IPwmService) {
        super($http, $log, $q, pwmService);
    }

    getOrgChartMaxParents(): IPromise<number> {
        return this.getValue(ORGCHART_MAX_PARENTS);
    }

    orgChartEnabled(): IPromise<boolean> {
        return this.getValue(ORGCHART_ENABLED)
            .then(null, () => { return true; }); // On error use default
    }

    orgChartShowChildCount(): IPromise<boolean> {
        return this.getValue(ORGCHART_SHOW_CHILD_COUNT);
    }

    personDetailsConfig(): IPromise<IPersonDetailsConfig> {
        const deffered: IDeferred<IPersonDetailsConfig> = this.$q.defer();

        this.$q.all([
            this.getValue(PHOTO_ENABLED),
            this.getValue(ORGCHART_ENABLED),
            this.getValue(EXPORT_ENABLED),
            this.getValue(EXPORT_MAX_DEPTH),
            this.getValue(MAILTO_ENABLED),
            this.getValue(MAILTO_MAX_DEPTH),
        ]).then((results: any[]) => {
            deffered.resolve({
                photosEnabled: results[0],
                orgChartEnabled: results[1],
                exportEnabled: results[2],
                maxExportDepth: results[3],
                emailTeamEnabled: results[4],
                maxEmailDepth: results[5]
            })
        });

        return deffered.promise;
    }

    advancedSearchConfig(): IPromise<IAdvancedSearchConfig> {
        return this.$q.all([
            this.getValue(ADVANCED_SEARCH_ENABLED),
            this.getValue(ADVANCED_SEARCH_MAX_ATTRIBUTES),
            this.getValue(ADVANCED_SEARCH_ATTRIBUTES)
        ]).then((result) => {
            return {
                enabled: result[0],
                maxRows: result[1],
                attributes: result[2]
            };
        });
    }
}
