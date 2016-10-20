import { IHttpPromise, IHttpService, IPromise } from 'angular';
import Person from '../models/person.model';

// declare var PWM_GLOBAL: any; // Comes from PWM

export interface IPeopleService {
    getOrgChartDataForUser(id: string): IPromise<Person[]>;
    getUserData(id: string): IPromise<Person>;
}

export default class PeopleService implements IPeopleService {
    static $inject = ['$http'];
    public constructor(private $http: IHttpService) {
    }

    public getOrgChartDataForUser(id: string): IHttpPromise<Person[]> {
        let url = /*PWM_GLOBAL['url-context'] + */'/private/peoplesearch?processAction=orgChartData';
        // url = PWM_MAIN.addPwmFormIDtoURL(url);
        return this.$http.post(url, { id: id, asParent: false });
    }

    public getUserData(id: string): IHttpPromise<Person> {
        let url = /*PWM_GLOBAL['url-context'] + */'/private/peoplesearch?processAction=detail';
        // url = PWM_MAIN.addPwmFormIDtoURL(url);
        return this.$http.post(url, { id: id });
    }
}
