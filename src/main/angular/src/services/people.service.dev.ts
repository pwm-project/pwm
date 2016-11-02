import { IPromise, IQService, ITimeoutService } from 'angular';
import Person from '../models/person.model';
import { IPeopleService } from './people.service';

var peopleData = require('./people.data');

export default class PeopleService implements IPeopleService {
    private people: Person[];
    static $inject = ['$q', '$timeout'];

    constructor(private $q: IQService, private $timeout: ITimeoutService) {
        this.people = peopleData.map((person) => new Person(person));
    }

    autoComplete(query: string): IPromise<Person[]> {
        return this.search(query);
    }

    getDirectReports(id: string): angular.IPromise<Person[]> {
        var deferred = this.$q.defer<Person[]>();

        this.$timeout(() => {
            var people = this.people.filter((person: Person) => person.detail['manager']['typeMetaData'].userKey == id);

            deferred.resolve(people);
        });

        return deferred.promise;
    }

    getManagementChain(id: string): angular.IPromise<Person[]> {
        var deferred = this.$q.defer<Person[]>();

        this.$timeout(() => {
            var person = this.findPerson(id);

            if (person) {
                var managementChain: Person[] = [];

                while (person = this.findPerson(person.detail['manager']['typeMetaData'].userKey)) {
                    managementChain.push(person);
                }

                deferred.resolve(managementChain);
            }
            else {
                deferred.reject(`Person with id: "${id}" not found.`);
            }
        });

        return deferred.promise;
    }

    getNumberOfDirectReports(personId: string): IPromise<number> {
        return this.getDirectReports(personId)
            .then((directReports: Person[]) => {
                return this.$q.resolve(directReports.length);
            });
    }

    getPerson(id: string): IPromise<Person> {
        var deferred = this.$q.defer<Person>();

        this.$timeout(() => {
            var person = this.findPerson(id);

            if (person) {
                deferred.resolve(person);
            }
            else {
                deferred.reject(`Person with id: "${id}" not found.`);
            }
        });

        return deferred.promise;
    }

    isOrgChartEnabled(id: string): angular.IPromise<boolean> {
        var deferred = this.$q.defer<boolean>();

        this.$timeout(() => {
            deferred.resolve(true);
        });

        return deferred.promise;
    }

    search(query: string): angular.IPromise<Person[]> {
        var deferred = this.$q.defer<Person[]>();
        var self = this;
        this.$timeout(() => {
            var people = self.people.filter((person: Person) =>
                person.detail.givenName['values'][0].toLowerCase().indexOf(query.toLowerCase()) >= 0);

            deferred.resolve(people);
        });

        return deferred.promise;
    }

    private findPerson(id: string): Person {
        var people = this.people.filter((person: Person) => person.userKey == id);

        if (people.length) {
            return people[0];
        }

        return null;
    }
}
