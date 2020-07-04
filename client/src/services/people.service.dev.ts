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

import { IPromise, IQService, ITimeoutService } from 'angular';
import { IPerson } from '../models/person.model';
import {IPeopleService, IQuery} from './people.service';
import IOrgChartData from '../models/orgchart-data.model';
import SearchResult from '../models/search-result.model';

const peopleData = require('./people.data.json');

const MAX_RESULTS = 10;
const SIMULATED_RESPONSE_TIME = 0;

export default class PeopleService implements IPeopleService {
    private people: IPerson[];

    static $inject = ['$q', '$timeout' ];
    constructor(private $q: IQService, private $timeout: ITimeoutService) {
        this.people = peopleData.map((person) => <IPerson>(person));

        // Create directReports detail (instead of managing this in people.data.json
        this.people.forEach((person: IPerson) => {
            const directReports = this.findDirectReports(person.userKey);

            if (!directReports.length) {
                return;
            }

            person.detail.directReports = {
                name: 'directReports',
                label: 'Direct Reports',
                type: 'userDN',
                userReferences: directReports
                    .map((directReport: IPerson) => {
                        return {
                            userKey: directReport.userKey,
                            displayName: directReport._displayName
                        };
                    })
            };
        }, this);
    }

    advancedSearch(queries: IQuery[]): IPromise<SearchResult> {
        let self = this;

        let deferred = this.$q.defer();
        let deferredAbort = this.$q.defer();

        let timeoutPromise = this.$timeout(() => {

            let people = this.getAdvancedSearchResults(queries);
            const sizeExceeded = (people.length > MAX_RESULTS);
            if (sizeExceeded) {
                people = people.slice(MAX_RESULTS);
            }

            deferred.resolve(new SearchResult({sizeExceeded: sizeExceeded, searchResults: people}));
        }, SIMULATED_RESPONSE_TIME * 6);

        // To simulate an abortable promise, edit SIMULATED_RESPONSE_TIME
        deferred.promise['_httpTimeout'] = deferredAbort;
        deferredAbort.promise.then(() => {
            self.$timeout.cancel(timeoutPromise);
            deferred.resolve();
        });

        return deferred.promise as IPromise<SearchResult>;
    }

    autoComplete(query: string): IPromise<IPerson[]> {
        return this.search(query)
            .then((searchResult: SearchResult) => {
                let people = searchResult.people;
                // Alphabetize results by _displayName
                people = people.sort((person1, person2) => person1._displayName.localeCompare(person2._displayName));

                if (people && people.length > 10) {
                    return people.slice(0, 10);
                }

                return people;
            });
    }

    getDirectReports(id: string): angular.IPromise<IPerson[]> {
        const people = this.findDirectReports(id);

        return this.$q.resolve(people);
    }

    getManagementChain(id: string, managementChainLimit): IPromise<IPerson[]> {
        let person = this.findPerson(id);

        if (person) {
            const managementChain: IPerson[] = [];

            while (person = this.findManager(person)) {
                managementChain.push(person);
            }

            return this.$q.resolve(managementChain);
        }

        return this.$q.reject(`Person with id: "${id}" not found.`);
    }

    getOrgChartData(personId: string): angular.IPromise<IOrgChartData> {
        if (!personId) {
            personId = '9';
        }

        const self = this.findPerson(personId);

        const orgChartData: IOrgChartData = {
            manager: this.findManager(self),
            children: this.findDirectReports(personId),
            self: self,
            assistant: this.findAssistant(self)
        };

        return this.$q.resolve(orgChartData);
    }

    getNumberOfDirectReports(personId: string): IPromise<number> {
        return this.getDirectReports(personId)
            .then((directReports: IPerson[]) => {
                return directReports.length;
            });
    }

    getPerson(id: string): IPromise<IPerson> {
        let self = this;

        let deferred = this.$q.defer();
        let deferredAbort = this.$q.defer();

        let timeoutPromise = this.$timeout(() => {
            const person = this.findPerson(id);

            if (person) {
                deferred.resolve(person);
            }
            else {
                deferred.reject(`Person with id: "${id}" not found.`);
            }
        }, SIMULATED_RESPONSE_TIME);

        // To simulate an abortable promise, edit SIMULATED_RESPONSE_TIME
        deferred.promise['_httpTimeout'] = deferredAbort;
        deferredAbort.promise.then(() => {
            self.$timeout.cancel(timeoutPromise);
            deferred.resolve();
        });

        return deferred.promise;
    }

    search(query: string): angular.IPromise<SearchResult> {
        let self = this;

        let deferred = this.$q.defer();
        let deferredAbort = this.$q.defer();

        let timeoutPromise = this.$timeout(() => {
            let people = this.people.filter((person: IPerson) => {
                if (!query) {
                    return false;
                }
                return person._displayName.toLowerCase().indexOf(query.toLowerCase()) >= 0;
            });

            const sizeExceeded = (people.length > MAX_RESULTS);
            if (sizeExceeded) {
                people = people.slice(MAX_RESULTS);
            }

            deferred.resolve(new SearchResult({sizeExceeded: sizeExceeded, searchResults: people}));
        }, SIMULATED_RESPONSE_TIME * 6);

        // To simulate an abortable promise, edit SIMULATED_RESPONSE_TIME
        deferred.promise['_httpTimeout'] = deferredAbort;
        deferredAbort.promise.then(() => {
            self.$timeout.cancel(timeoutPromise);
            deferred.resolve();
        });

        return deferred.promise as IPromise<SearchResult>;
    }

    private findDirectReports(id: string): IPerson[] {
        return this.people.filter((person: IPerson) => person.detail['manager']['userReferences'][0].userKey == id);
    }

    private findAssistant(person: IPerson): IPerson {
        if (!('assistant' in person.detail)) {
            return null;
        }

        return this.findPerson(person.detail['assistant']['userReferences'][0].userKey);
    }

    private findManager(person: IPerson): IPerson {
        return this.findPerson(person.detail['manager']['userReferences'][0].userKey);
    }

    private findPerson(id: string): IPerson {
        const people = this.people.filter((person: IPerson) => person.userKey == id);

        if (people.length) {
            return people[0];
        }

        return null;
    }

    private getAdvancedSearchResults(queries: IQuery[]): IPerson[] {
        let people = queries.length ? this.people : [];

        queries.forEach((query: IQuery) => {
            people = people.filter((person: IPerson) => {
                if (!query.value) {
                    return false;
                }

                let property = person[query.name];

                if (!property) {
                    return false;
                }

                if (typeof property === 'object' || typeof property === 'number') {
                    property = JSON.stringify(property);
                }

                return property.toLowerCase().indexOf(query.value.toLowerCase()) >= 0;
            });
        });

        return people;
    }
}
