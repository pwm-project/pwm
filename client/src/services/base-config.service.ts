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

import {IHttpService, ILogService, IPromise, IQService} from 'angular';
import {IPwmService} from './pwm.service';

const PHOTO_ENABLED = 'peoplesearch_enablePhoto';

export interface IConfigService {
    getColumnConfig(): IPromise<any>;
    getValue(key: string): IPromise<any>;
    photosEnabled(): IPromise<boolean>;
}

export abstract class ConfigBaseService implements IConfigService {

    constructor(protected $http: IHttpService,
                protected $log: ILogService,
                protected $q: IQService,
                protected pwmService: IPwmService) {
    }

    abstract getColumnConfig(): IPromise<any>;

    private getEndpointValue(endpoint: string, key: string): IPromise<any> {
        return this.$http
            .get(endpoint, { cache: true })
            .then((response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                return this.$q.resolve(response.data['data'][key]);
            }, this.handleHttpError);
    }

    private getPeopleSearchValue(key: string): IPromise<any> {
        let endpoint: string = this.pwmService.getPeopleSearchServerUrl('clientData');
        return this.getEndpointValue(endpoint, key);
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
        return this.getPeopleSearchValue(PHOTO_ENABLED)
            .then(null, () => { return this.$q.resolve(true); }); // On error use default
    }
}
