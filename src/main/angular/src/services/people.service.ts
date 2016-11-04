import { IHttpPromise, IHttpService, IPromise, IQService } from 'angular';
import Person from '../models/person.model';

// These come from legacy PWM:
declare var PWM_GLOBAL: any;
declare var PWM_MAIN: any;

export interface IPeopleService {
    autoComplete(query: string): IPromise<Person[]>;
    getDirectReports(personId: string): IPromise<Person[]>;
    getNumberOfDirectReports(personId: string): IPromise<number>;
    getManagementChain(personId: string): IPromise<Person[]>;
    getPerson(id: string): IPromise<Person>;
    isOrgChartEnabled(id: string): IPromise<boolean>;
    search(query: string): IPromise<Person[]>;
}

export default class PeopleService implements IPeopleService {

    static $inject = ['$http', '$q'];
    constructor(private $http: IHttpService, private $q: IQService) {
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

    getDirectReports(id: string): angular.IPromise<Person[]> {
        return this.$http.post(this.getServerUrl('orgChartData'), {
            userKey: id
        }).then((response) => {
            let people: Person[] = [];

            for (let directReport of response.data['data']['children']) {
                let person: Person = new Person(directReport);
                people.push(person);
            }

            return this.$q.resolve(people);
        });
    }

    getNumberOfDirectReports(personId: string): angular.IPromise<number> {
        return this.$http.post(this.getServerUrl('orgChartData'), {
            userKey: personId
        }).then((response) => {
            return this.$q.resolve(response.data['data']['children'].length);
        });
    }

    getManagementChain(id: string): angular.IPromise<Person[]> {
        let people: Person[] = [];
        return this.getManagerRecursive(id, people);
    }

    private getManagerRecursive(id: string, people: Person[]): angular.IPromise<Person[]> {
        return this.$http.post(this.getServerUrl('orgChartData'), {
            userKey: id
        }).then((response) => {
            let responseData = response.data['data'];
            if ('parent' in responseData) {
                let manager: Person = responseData['parent'];
                people.push(manager);

                return this.getManagerRecursive(manager.userKey, people);
            }

            return this.$q.resolve(people);
        });
    }

    getPerson(id: string): IPromise<Person> {
        return this.$http.post(this.getServerUrl('detail'), {
            userKey: id
        }).then((response) => {
            let person: Person = new Person(response.data['data']);
            return this.$q.resolve(person);
        });
    }

    isOrgChartEnabled(id: string): angular.IPromise<boolean> {
        // TODO: need to read this from the server
        return this.$q.resolve(true);
    }

    search(query: string): angular.IPromise<Person[]> {
        return this.$http.post(this.getServerUrl('search'), {
            username: query
        }).then((response) => {
            let people: Person[] = [];

            for (let searchResult of response.data['data']['searchResults']) {
                people.push(new Person(searchResult));
            }

            return this.$q.resolve(people);
        });
    }

    private getServerUrl(processAction: string): string {
        let url: string = PWM_GLOBAL['url-context'] + '/private/peoplesearch?processAction=' + processAction;
        url = PWM_MAIN.addPwmFormIDtoURL(url);
        return url;
    }
}
