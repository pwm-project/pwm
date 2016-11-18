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
import Person from '../models/person.model';
import PwmService from './pwm.service';
import OrgChartData from '../models/orgchart-data.model';
import SearchResult from '../models/search-result.model';

export interface IPeopleService {
    autoComplete(query: string): IPromise<Person[]>;
    cardSearch(query: string): IPromise<SearchResult>;
    getDirectReports(personId: string): IPromise<Person[]>;
    getNumberOfDirectReports(personId: string): IPromise<number>;
    getManagementChain(personId: string): IPromise<Person[]>;
    getOrgChartData(personId: string): IPromise<OrgChartData>;
    getPerson(id: string): IPromise<Person>;
    isOrgChartEnabled(id: string): IPromise<boolean>;
    search(query: string): IPromise<SearchResult>;
}

export default class PeopleService implements IPeopleService {
    static $inject = ['$http', '$q', 'PwmService' ];
    constructor(private $http: IHttpService, private $q: IQService, private pwmService: PwmService) {}

    autoComplete(query: string): IPromise<Person[]> {
        return this.search(query, { 'includeDisplayName': true })
            .then((searchResult: SearchResult) => {
                let people = searchResult.people;
                console.log(people);
                if (people && people.length > 10) {
                    return this.$q.resolve(people.slice(0, 10));
                }

                return this.$q.resolve(people);
            });
    }

    cardSearch(query: string): angular.IPromise<SearchResult> {
        let self = this;

        return this.search(query)
            .then((searchResult: SearchResult) => {
                let sizeExceeded = searchResult.sizeExceeded;

                let peoplePromises: IPromise<Person>[] = searchResult.people.map((person: Person) => {
                    return self.getPerson(person.userKey);
                });

                return this.$q
                    .all(peoplePromises)
                    .then((people: Person[]) => {
                        let searchResult = new SearchResult({ sizeExceeded: sizeExceeded, searchResults: people });
                        return this.$q.resolve(searchResult);
                    });
            });
    }

    getDirectReports(id: string): IPromise<Person[]> {
        return this.getOrgChartData(id).then((orgChartData: OrgChartData) => {
            let people: Person[] = [];

            for (let directReport of orgChartData.children) {
                let person: Person = new Person(directReport);
                people.push(person);
            }

            return this.$q.resolve(people);
        });
    }

    getNumberOfDirectReports(personId: string): IPromise<number> {
        return this.getOrgChartData(personId).then((orgChartData: OrgChartData) => {
            return this.$q.resolve(orgChartData.children.length);
        });
    }

    getManagementChain(id: string): IPromise<Person[]> {
        let people: Person[] = [];
        return this.getManagerRecursive(id, people);
    }

    private getManagerRecursive(id: string, people: Person[]): IPromise<Person[]> {
        return this.getOrgChartData(id).then((orgChartData: OrgChartData) => {
            if (orgChartData.manager) {
                people.push(orgChartData.manager);

                return this.getManagerRecursive(orgChartData.manager.userKey, people);
            }

            return this.$q.resolve(people);
        });
    }

    getOrgChartData(personId: string): angular.IPromise<OrgChartData> {
        return this.$http
            .get(this.pwmService.getServerUrl('orgChartData'), { cache: true, params: { userKey: personId } })
            .then((response) => {
                let responseData = response.data['data'];

                let manager: Person;
                if ('parent' in responseData) { manager = new Person(responseData['parent']); }
                const children = responseData['children'].map((child: any) => new Person(child));
                const self = new Person(responseData['self']);

                return this.$q.resolve(new OrgChartData(manager, children, self));
            });
    }

    getPerson(id: string): IPromise<Person> {
        return this.$http
            .get(this.pwmService.getServerUrl('detail'), { cache: true, params: { userKey: id } })
            .then((response) => {
                let person: Person = new Person(response.data['data']);
                return this.$q.resolve(person);
            });
    }

    isOrgChartEnabled(id: string): IPromise<boolean> {
        // TODO: need to read this from the server
        return this.$q.resolve(true);
    }

    search(query: string, params?: any): IPromise<SearchResult> {
        return this.$http
            .get(
                this.pwmService.getServerUrl('search', { 'includeDisplayName': true }),
                { cache: true, params: { username: query } }
            )
            .then((response) => {
                let receivedData: any = response.data['data'];
                let searchResult: SearchResult = new SearchResult(receivedData);

                return this.$q.resolve(searchResult);
            });
    }
}
