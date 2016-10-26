import { IHttpPromise, IHttpService, IPromise, IQService } from 'angular';
import Person from '../models/person.model';

// These come from legacy PWM:
declare var PWM_GLOBAL: any;
declare var PWM_MAIN: any;

export interface IPeopleService {
    getDirectReports(personId: string): IPromise<Person[]>;
    getManagementChain(personId: string): IPromise<Person[]>;
    getPerson(id: string): IPromise<Person>;
    isOrgChartEnabled(id: string): IPromise<boolean>;
    search(query: string): IPromise<Person[]>;
}

export default class PeopleService implements IPeopleService {
    static $inject = ['$http', '$q'];
    constructor(private $http: IHttpService, private $q: IQService) {
    }

    getDirectReports(id: string): angular.IPromise<Person[]> {
        let deferred = this.$q.defer();

        this.$http.post(this.getServerUrl('orgChartData'), {
            userKey: id
        }).then((response) => {
            let people: Person[] = [];

            for (let directReport of response.data['data']['children']) {
                people.push(new Person(directReport));
            }

            deferred.resolve(people);
        }).catch((result) => {
            deferred.reject(result);
        });

        return deferred.promise;
    }

    getManagementChain(id: string): angular.IPromise<Person[]> {
        let deferred = this.$q.defer();

        this.$http.post(this.getServerUrl('orgChartData'), {
            userKey: id
        }).then((response) => {
            let people: Person[] = [];

            let person: Person = response.data['data']['parent'];
            people.push(person);

            deferred.resolve(people);
        }).catch((result) => {
            deferred.reject(result);
        });

        return deferred.promise;
    }

    getPerson(id: string): IPromise<Person> {
        let deferred = this.$q.defer();

        this.$http.post(this.getServerUrl('detail'), {
            userKey: id
        }).then((response) => {
            let person: Person = new Person(response.data['data']);
            deferred.resolve(person);
        }).catch((result) => {
            deferred.reject(result);
        });

        return deferred.promise;
    }

    isOrgChartEnabled(id: string): angular.IPromise<boolean> {
        return undefined;
    }

    search(query: string): angular.IPromise<Person[]> {
        let deferred = this.$q.defer();

        this.$http.post(this.getServerUrl('search'), {
            username: query
        }).then((response) => {
            let people: Person[] = [];

            for (let searchResult of response.data['data']['searchResults']) {
                people.push(new Person(searchResult));
            }

            deferred.resolve(people);
        }).catch((result) => {
            deferred.reject(result);
        });

        return deferred.promise;
    }

    private getServerUrl(processAction: string): string {
        let url: string = PWM_GLOBAL['url-context'] + '/private/peoplesearch?processAction=' + processAction;
        url = PWM_MAIN.addPwmFormIDtoURL(url);

        return url;
    }
}
