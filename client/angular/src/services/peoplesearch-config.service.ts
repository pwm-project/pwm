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
        return this.$q.all([
            this.getValue(PHOTO_ENABLED),
            this.getValue(ORGCHART_ENABLED),
            this.getValue(EXPORT_ENABLED),
            this.getValue(EXPORT_MAX_DEPTH),
            this.getValue(MAILTO_ENABLED),
            this.getValue(MAILTO_MAX_DEPTH),
        ]).then((results: any[]) => {
            return {
                photosEnabled: results[0],
                orgChartEnabled: results[1],
                exportEnabled: results[2],
                maxExportDepth: results[3],
                emailTeamEnabled: results[4],
                maxEmailDepth: results[5]
            }
        });
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
