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


import { IPromise, IQService } from 'angular';
import {ConfigBaseService} from './base-config.service.dev';
import {IConfigService} from './base-config.service';
import {AdvancedSearchConfig, IPeopleSearchConfigService} from './peoplesearch-config.service';


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

    advancedSearchConfig(): IPromise<AdvancedSearchConfig> {
        return this.$q.resolve({
            enabled: true,
            maxRows: 3,
            attributes: [
                {
                    id: 'title',
                    attribute: 'Title'
                },
                {
                    id: 'givenName',
                    attribute: 'Given Name'
                },
                {
                    id: 'sn',
                    attribute: 'First Name'
                }
            ]
        });
    }
}
