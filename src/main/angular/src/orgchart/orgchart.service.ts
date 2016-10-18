/// <reference types="angular" />

declare var PWM_GLOBAL: any; // Comes from PWM
declare var PWM_MAIN: any;

export class OrgChartService {
    static $inject = ['$http'];
    public constructor(private $http: ng.IHttpService) {
    }

    public getOrgChartData(userKey: string) {
        let url: string = PWM_GLOBAL['url-context'] + '/private/peoplesearch?processAction=orgChartData';
        url = PWM_MAIN.addPwmFormIDtoURL(url);
        return this.$http.post(url, { userKey: userKey, asParent: false });
    }

    public getUserData(userKey: string) {
        let url: string = PWM_GLOBAL['url-context'] + '/private/peoplesearch?processAction=detail';
        url = PWM_MAIN.addPwmFormIDtoURL(url);
        return this.$http.post(url, { userKey: userKey });
    }
}
