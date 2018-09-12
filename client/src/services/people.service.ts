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


import {IHttpService, ILogService, IPromise, IQService, IWindowService} from 'angular';
import {IPerson} from '../models/person.model';
import IPwmService from './pwm.service';
import IOrgChartData from '../models/orgchart-data.model';
import SearchResult from '../models/search-result.model';
import {IPeopleSearchConfigService} from './peoplesearch-config.service';

export interface IPeopleService {
    autoComplete(query: string): IPromise<IPerson[]>;

    getDirectReports(personId: string): IPromise<IPerson[]>;

    getNumberOfDirectReports(personId: string): IPromise<number>;

    getManagementChain(id: string, managementChainLimit): IPromise<IPerson[]>;

    getOrgChartData(personId: string, skipChildren: boolean): IPromise<IOrgChartData>;

    getPerson(id: string): IPromise<IPerson>;

    search(query: string): IPromise<SearchResult>;
}

export default class PeopleService implements IPeopleService {
    PWM_GLOBAL: any;

    static $inject = ['$http', '$log', '$q', 'ConfigService', 'PwmService', '$window'];

    constructor(private $http: IHttpService,
                private $log: ILogService,
                private $q: IQService,
                private configService: IPeopleSearchConfigService,
                private pwmService: IPwmService,
                $window: IWindowService) {
        if ($window['PWM_GLOBAL']) {
            this.PWM_GLOBAL = $window['PWM_GLOBAL'];
        }
        else {
            this.$log.warn('PWM_GLOBAL is not defined on window');
        }
    }

    autoComplete(query: string): IPromise<IPerson[]> {
        return this.search(query, {'includeDisplayName': true})
            .then((searchResult: SearchResult) => {
                let people = searchResult.people;

                if (people && people.length > 10) {
                    return people.slice(0, 10);
                }

                return people;
            });
    }

    getDirectReports(id: string): IPromise<IPerson[]> {
        return this.getOrgChartData(id, false).then((orgChartData: IOrgChartData) => {
            let people: IPerson[] = [];

            for (let directReport of orgChartData.children) {
                let person: IPerson = <IPerson>(directReport);
                people.push(person);
            }

            return people;
        });
    }

    getNumberOfDirectReports(id: string): IPromise<number> {
        return this.getDirectReports(id).then((people: IPerson[]) => {
            return people.length;
        });
    }

    getManagementChain(id: string, managementChainLimit): IPromise<IPerson[]> {
        let people: IPerson[] = [];
        return this.getManagerRecursive(id, people, managementChainLimit);
    }

    private getManagerRecursive(id: string, people: IPerson[], managementChainLimit): IPromise<IPerson[]> {
        return this.getOrgChartData(id, true)
            .then((orgChartData: IOrgChartData) => {
                if (orgChartData.manager && people.length < managementChainLimit) {
                    people.push(orgChartData.manager);

                    return this.getManagerRecursive(orgChartData.manager.userKey, people, managementChainLimit);
                }

                return people;
            });
    }

    getOrgChartData(personId: string, noChildren: boolean): angular.IPromise<IOrgChartData> {
        return this.$http
            .get(this.pwmService.getServerUrl('orgChartData'), {
                cache: true,
                params: {
                    userKey: personId,
                    noChildren: noChildren
                }
            })
            .then(
                (response) => {
                    if (response.data['error']) {
                        return this.handlePwmError(response);
                    }

                    let responseData = response.data['data'];

                    let manager: IPerson;
                    let assistant: IPerson;

                    if ('parent' in responseData) {
                        manager = <IPerson>(responseData['parent']);
                    }
                    if ('assistant' in responseData) {
                        assistant = <IPerson>(responseData['assistant']);
                    }

                    const children = responseData['children'].map((child: any) => <IPerson>(child));
                    const self = <IPerson>(responseData['self']);

                    return {
                        manager: manager,
                        children: children,
                        self: self,
                        assistant: assistant
                    };
                },
                this.handleHttpError.bind(this));
    }

    getPerson(id: string): IPromise<IPerson> {
        // Deferred object used for aborting requests. See promise.service.ts for more information
        let httpTimeout = this.$q.defer();

        let request = this.$http
            .get(this.pwmService.getServerUrl('detail'), {
                cache: true,
                params: {userKey: id},
                timeout: httpTimeout.promise
            });

        let promise = request.then(
            (response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                let person: IPerson = <IPerson>(response.data['data']);
                return person;
            },
            this.handleHttpError.bind(this));

        promise['_httpTimeout'] = httpTimeout;

        return promise;
    }

    search(query: string, params?: any): IPromise<SearchResult> {
        // Deferred object used for aborting requests. See promise.service.ts for more information
        let httpTimeout = this.$q.defer();

        let formID: string = encodeURIComponent('&pwmFormID=' + this.PWM_GLOBAL['pwmFormID']);
        let url: string = this.pwmService.getServerUrl('search', params)
            + '&pwmFormID=' + this.PWM_GLOBAL['pwmFormID'];
        let request = this.$http
            .post(url, {
                username: query,
                pwmFormID: formID
            }, {
                cache: true,
                timeout: httpTimeout.promise,
                headers: {'Content-Type': 'multipart/form-data'},
            });

        let promise = request.then(
            (response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                let receivedData: any = response.data['data'];
                let searchResult: SearchResult = new SearchResult(receivedData);

                return searchResult;
            },
            this.handleHttpError.bind(this));

        promise['_httpTimeout'] = httpTimeout;

        return promise;
    }

    private handleHttpError(error): void {
        this.$log.error(error);
    }

    private handlePwmError(response): IPromise<any> {
        const errorMessage = `${response.data['errorCode']}: ${response.data['errorMessage']}`;
        this.$log.error(errorMessage);

        return this.$q.reject(response.data['errorMessage']);
    }
}
