import Person from '../models/person.model';
import { IPeopleService } from '../services/people.service';
import { IRootScopeService } from 'angular';
import * as angular from 'angular';

export default class PeopleSearchService {
    people: Person[];

    static $inject = ['$rootScope', 'PeopleService'];
    public constructor(private $rootScope: IRootScopeService, private peopleService: IPeopleService) {
        this.people = [];
    }

    search(query: string) {
        if (angular.isString(query)) {
            this.peopleService.search(query)
                .then((people: Person[]) => {
                    this.setPeople(people);
                })
                .catch((result) => {
                    console.log(result);
                });
        }
        else {
            this.setPeople([]);
        }
    }

    notify() {
        this.$rootScope.$broadcast('people-updated');
    }

    setPeople(people: Person[]) {
        this.people = people;
        this.notify();
    }
}
