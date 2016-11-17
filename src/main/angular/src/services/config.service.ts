import { IHttpService, IPromise, IQService } from 'angular';
import PwmService from './pwm.service';

export interface IConfigService {
    getColumnConfiguration(): IPromise<any>;
}

export default class ConfigService implements IConfigService {
    static $inject = ['$http', '$q', PwmService ];
    constructor(private $http: IHttpService,
                private $q: IQService,
                private pwmService: PwmService) {
    }

    getColumnConfiguration(): IPromise<any> {
        return this.$http
            .get(this.pwmService.getServerUrl('clientData'), { cache: true })
            .then((response) => {
                return this.$q.resolve(response.data['data']['peoplesearch_search_columns']);
            });
    }
}
