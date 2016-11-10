import { IPromise, IQService, ITimeoutService } from 'angular';
import Person from '../models/person.model';
import { IPeopleService } from './people.service';

var peopleData = require('./people.data');

export default class PeopleService implements IPeopleService {
    private people: Person[];

    static $inject = ['$q'];
    constructor(private $q: IQService) {
        this.people = peopleData.map((person) => new Person(person));
    }

    autoComplete(query: string): IPromise<Person[]> {
        return this.search(query)
            .then((people: Person[]) => {
                if (people && people.length > 10) {
                    return this.$q.resolve(people.slice(0, 10));
                }

                return this.$q.resolve(people);
            });
    }

    cardSearch(query: string): angular.IPromise<Person[]> {
        return this.search(query);
    }

    getDirectReports(id: string): angular.IPromise<Person[]> {
        var people = this.people.filter((person: Person) => person.detail['manager']['typeMetaData'].userKey == id);

        return this.$q.resolve(people);
    }

    getManagementChain(id: string): angular.IPromise<Person[]> {
        var person = this.findPerson(id);

        if (person) {
            var managementChain: Person[] = [];

            while (person = this.findPerson(person.detail['manager']['typeMetaData'].userKey)) {
                managementChain.push(person);
            }

            return this.$q.resolve(managementChain);
        }

        return this.$q.reject(`Person with id: "${id}" not found.`);
    }

    getNumberOfDirectReports(personId: string): IPromise<number> {
        return this.getDirectReports(personId)
            .then((directReports: Person[]) => {
                return this.$q.resolve(directReports.length);
            });
    }

    getPerson(id: string): IPromise<Person> {
        var person = this.findPerson(id);

        if (person) {
            return this.$q.resolve(person);
        }

        return this.$q.reject(`Person with id: "${id}" not found.`);
    }

    isOrgChartEnabled(id: string): angular.IPromise<boolean> {
        return this.$q.resolve(true);
    }

    search(query: string): angular.IPromise<Person[]> {
        var people = this.people.filter((person: Person) => {
            if (!query) {
                return false;
            }
            var fullName = `${person.givenName} ${person.sn}`;
            return fullName.toLowerCase().indexOf(query.toLowerCase()) >= 0;
        });

        return this.$q.resolve(people);

    }

    private findPerson(id: string): Person {
        var people = this.people.filter((person: Person) => person.userKey == id);

        if (people.length) {
            return people[0];
        }

        return null;
    }
}
