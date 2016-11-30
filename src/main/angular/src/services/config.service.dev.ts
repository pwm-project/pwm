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


import { IConfigService } from './config.service';
import { IPromise, IQService } from 'angular';

export default class ConfigService implements IConfigService {
    static $inject = [ '$q' ];
    constructor(private $q: IQService) {}

    getColumnConfig(): IPromise<any> {
        return this.$q.resolve({
            givenName: 'First Name',
            sn: 'Last Name',
            title: 'Title',
            mail: 'Email',
            telephoneNumber: 'Telephone'
        });
    }

    photosEnabled(): IPromise<boolean> {
        return this.$q.resolve(false);
    }

    orgChartEnabled(): IPromise<boolean> {
        return this.$q.resolve(true);
    };

    getValue(key: string): IPromise<any> {
        return null;
    }
}
