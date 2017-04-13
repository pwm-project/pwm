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


import { isString, IHttpService, ILogService, IPromise, IQService } from 'angular';
import { IPerson } from '../models/person.model';
import IPwmService from './pwm.service';
import OrgChartData from '../models/orgchart-data.model';
import SearchResult from '../models/search-result.model';

export interface IPeopleService {
    autoComplete(query: string): IPromise<IPerson[]>;
    getDirectReports(personId: string): IPromise<IPerson[]>;
    getNumberOfDirectReports(personId: string): IPromise<number>;
    getManagementChain(personId: string): IPromise<IPerson[]>;
    getOrgChartData(personId: string, skipChildren: boolean): IPromise<OrgChartData>;
    getPerson(id: string): IPromise<IPerson>;
    search(query: string): IPromise<SearchResult>;
}

export default class PeopleService implements IPeopleService {
    static $inject = ['$http', '$log', '$q', 'PwmService' ];
    constructor(private $http: IHttpService,
                private $log: ILogService,
                private $q: IQService,
                private pwmService: IPwmService) {}

    autoComplete(query: string): IPromise<IPerson[]> {
        return this.search(query, { 'includeDisplayName': true })
            .then((searchResult: SearchResult) => {
                let people = searchResult.people;

                if (people && people.length > 10) {
                    return this.$q.resolve(people.slice(0, 10));
                }

                return this.$q.resolve(people);
            });
    }

    getDirectReports(id: string): IPromise<IPerson[]> {
        return this.getOrgChartData(id, false).then((orgChartData: OrgChartData) => {
            let people: IPerson[] = [];

            for (let directReport of orgChartData.children) {
                let person: IPerson = <IPerson>(directReport);
                people.push(person);
            }

            return this.$q.resolve(people);
        });
    }

    getNumberOfDirectReports(id: string): IPromise<number> {
        return this.getDirectReports(id).then((people: IPerson[]) => {
            return this.$q.resolve(people.length);
        });
    }

    getManagementChain(id: string): IPromise<IPerson[]> {
        let people: IPerson[] = [];
        return this.getManagerRecursive(id, people);
    }

    private getManagerRecursive(id: string, people: IPerson[]): IPromise<IPerson[]> {
        return this.getOrgChartData(id, true)
            .then((orgChartData: OrgChartData) => {
                if (orgChartData.manager) {
                    people.push(orgChartData.manager);

                    return this.getManagerRecursive(orgChartData.manager.userKey, people);
                }

                return this.$q.resolve(people);
            });
    }

    getOrgChartData(personId: string, noChildren: boolean): angular.IPromise<OrgChartData> {
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
                    if ('parent' in responseData) { manager = <IPerson>(responseData['parent']); }
                    const children = responseData['children'].map((child: any) => <IPerson>(child));
                    const self = <IPerson>(responseData['self']);

                    return this.$q.resolve(new OrgChartData(manager, children, self));
                },
                this.handleHttpError.bind(this));
    }

    getPerson(id: string): IPromise<IPerson> {
        // Deferred object used for aborting requests. See promise.service.ts for more information
        let httpTimeout = this.$q.defer();

        let request = this.$http
            .get(this.pwmService.getServerUrl('detail'), {
                cache: true,
                params: { userKey: id },
                timeout: httpTimeout.promise
            });

        let promise = request.then(
            (response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                let person: IPerson = <IPerson>(response.data['data']);
                return this.$q.resolve(person);
            },
            this.handleHttpError.bind(this));

        promise['_httpTimeout'] = httpTimeout;

        return promise;
    }

    search(query: string, params?: any): IPromise<SearchResult> {
        // Deferred object used for aborting requests. See promise.service.ts for more information
        let httpTimeout = this.$q.defer();

        let request = this.$http
            .get(this.pwmService.getServerUrl('search', params), {
                cache: true,
                params: { username: query },
                timeout: httpTimeout.promise
            });

        let promise = request.then(
            (response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                let receivedData: any = response.data['data'];
                let searchResult: SearchResult = new SearchResult(receivedData);

                return this.$q.resolve(searchResult);
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
