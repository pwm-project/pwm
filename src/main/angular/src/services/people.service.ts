import { IHttpPromise, IHttpService, IPromise } from 'angular';
import Person from '../models/person.model';

// declare var PWM_GLOBAL: any; // Comes from PWM

export interface IPeopleService {
    getDirectReports(personId: string): IPromise<Person[]>;
    getManagementChain(personId: string): IPromise<Person[]>;
    getPerson(id: string): IPromise<Person>;
}

export default class PeopleService implements IPeopleService {
    static $inject = ['$http'];
    constructor(private $http: IHttpService) {
    }

    getDirectReports(id: string): angular.IPromise<Person[]> {
        return undefined;
    }

    getManagementChain(id: string): angular.IPromise<Person[]> {
        return undefined;
    }

    getPerson(id: string): IHttpPromise<Person> {
        return undefined;
    }
}
