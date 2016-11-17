import { IHttpService, IPromise, IQService } from 'angular';
import Person from '../models/person.model';
import PwmService from './pwm.service';
import OrgChartData from '../models/orgchart-data.model';

export interface IPeopleService {
    autoComplete(query: string): IPromise<Person[]>;
    cardSearch(query: string): IPromise<Person[]>;
    getDirectReports(personId: string): IPromise<Person[]>;
    getNumberOfDirectReports(personId: string): IPromise<number>;
    getManagementChain(personId: string): IPromise<Person[]>;
    getOrgChartData(personId: string): IPromise<OrgChartData>;
    getPerson(id: string): IPromise<Person>;
    isOrgChartEnabled(id: string): IPromise<boolean>;
    search(query: string): IPromise<Person[]>;
}

export default class PeopleService extends PwmService implements IPeopleService {
    static $inject = ['$http', '$q'];
    constructor(private $http: IHttpService, private $q: IQService) {
        super();
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
        var self = this;
        return this.search(query)
            .then((people: Person[]) => {
                var peoplePromises: IPromise<Person>[] = people.map((person: Person) => {
                    return self.getPerson(person.userKey);
                });

                return this.$q.all(peoplePromises);
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

        return this.$http.get(this.getServerUrl('orgChartData'), { params: { userKey: personId } })
            .then((response) => {
                let responseData = response.data['data'];

                var manager: Person;
                if ('parent' in responseData) { manager = new Person(responseData['parent']); }
                var children = responseData['children'].map((child: any) => new Person(child));
                var self = new Person(responseData['self']);

                var orgChartData = new OrgChartData(manager, children, self);

                return this.$q.resolve(orgChartData);
            });
    }

    getPerson(id: string): IPromise<Person> {
        return this.$http.get(this.getServerUrl('detail'), { params: { userKey: id } })
            .then((response) => {
            let person: Person = new Person(response.data['data']);
            return this.$q.resolve(person);
        });
    }

    isOrgChartEnabled(id: string): IPromise<boolean> {
        // TODO: need to read this from the server
        return this.$q.resolve(true);
    }

    search(query: string): IPromise<Person[]> {
        return this.$http.get(this.getServerUrl('search', { 'includeDisplayName': true }), { params: { username: query } })
        .then((response) => {
            let people: Person[] = [];

            for (let searchResult of response.data['data']['searchResults']) {
                people.push(new Person(searchResult));
            }

            return this.$q.resolve(people);
        });
    }
}
