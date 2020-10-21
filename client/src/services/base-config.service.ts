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

import {IHttpService, ILogService, IPromise, IQService} from 'angular';
import {IPwmService} from './pwm.service';

const COLUMN_CONFIG = 'searchColumns';
export const PHOTO_ENABLED = 'enablePhoto';
const PRINTING_ENABLED = 'enableOrgChartPrinting';

export const ADVANCED_SEARCH_ENABLED = 'enableAdvancedSearch';
export const ADVANCED_SEARCH_MAX_ATTRIBUTES = 'maxAdvancedSearchAttributes';
export const ADVANCED_SEARCH_ATTRIBUTES = 'advancedSearchAttributes';

export interface IConfigService {
    getColumnConfig(): IPromise<any>;
    getValue(key: string): IPromise<any>;
    photosEnabled(): IPromise<boolean>;
    printingEnabled(): IPromise<boolean>;
}

export interface IAttributeMetadata {
    attribute: string;
    label: string;
    type: string;
    options: any;
}

export interface IAdvancedSearchConfig {
    enabled: boolean;
    maxRows: number;
    attributes: IAttributeMetadata[];
}

export interface IAdvancedSearchQuery {
    key: string;
    value: string;
}

export abstract class ConfigBaseService implements IConfigService {

    constructor(protected $http: IHttpService,
                protected $log: ILogService,
                protected $q: IQService,
                protected pwmService: IPwmService) {
    }

    getColumnConfig(): IPromise<any> {
        return this.getValue(COLUMN_CONFIG);
    }

    private getEndpointValue(endpoint: string, key: string): IPromise<any> {
        return this.$http
            .get(endpoint, { cache: true })
            .then((response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                return response.data['data'][key];
            }, this.handleHttpError);
    }

    getValue(key: string): IPromise<any> {
        let endpoint: string = this.pwmService.getServerUrl('clientData');
        return this.getEndpointValue(endpoint, key);
    }

    private handleHttpError(error): void {
        this.$log.error(error);
    }

    private handlePwmError(response): IPromise<any> {
        const errorMessage = `${response.data['errorCode']}: ${response.data['errorMessage']}`;
        this.$log.error(errorMessage);

        return this.$q.reject(response.data['errorMessage']);
    }

    photosEnabled(): IPromise<boolean> {
        return this.getValue(PHOTO_ENABLED)
            .then(null, () => { return true; }); // On error use default
    }

    printingEnabled(): IPromise<boolean> {
        return this.getValue(PRINTING_ENABLED)
            .then(null, () => { return true; }); // On error use default
    }
}
