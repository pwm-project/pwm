/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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


import { IHttpService, IPromise, IQService } from 'angular';
import PwmService from './pwm.service';

export interface IConfigService {
    getColumnConfiguration(): IPromise<any>;
}

export default class ConfigService implements IConfigService {
    static $inject = ['$http', '$q', 'PwmService' ];
    constructor(private $http: IHttpService,
                private $q: IQService,
                private pwmService: PwmService) {
    }

    getColumnConfiguration(): IPromise<any> {
        return this.$http
            .get(this.pwmService.getServerUrl('clientData'), { cache: true })
            .then((response) => {
                return this.$q.resolve(response.data['data']['peoplesearch_search_columns']);
            });
    }
}
