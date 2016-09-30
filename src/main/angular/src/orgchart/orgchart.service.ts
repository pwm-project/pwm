declare var PWM_GLOBAL: any; // Comes from PWM

export class OrgChartService {
    static $inject = ['$http'];
    public constructor(private $http) {
    }

    public getOrgChartData() {
        return this.$http.get(PWM_GLOBAL['url-context'] + '/public/resources/app/orgchart/orgchart.data.json');
    }
}
