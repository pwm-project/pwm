import { IPromise, IQService, ITimeoutService } from 'angular';
import Person from '../models/person.model';
import { IPeopleService } from './people.service';

var peopleData = require('./people.data');

export default class PeopleService implements IPeopleService {
    private people: Person[];

    static $inject = ['$q', '$timeout'];
    public constructor(private $q: IQService, private $timeout: ITimeoutService) {
        this.people = peopleData.map((person) => new Person(person));
    }

    public getOrgChartDataForUser(id: string): IPromise<Person[]> {
        return null;
    }

    public getUserData(id: string): IPromise<Person> {
        var deferred = this.$q.defer<Person>();

        this.$timeout(() => {
            var people = this.people.filter((person: Person) => person.id == id);

            if (people.length) {
                deferred.resolve(people[0]);
            }
            else {
                deferred.reject(`Person with id: "${id}" not found.`);
            }
        });

        return deferred.promise;
    }
}
