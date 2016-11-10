import { IHttpService, IPromise, IQService } from 'angular';
import PwmService from './pwm.service';

export interface IConfigService {
    getColumnConfiguration(): IPromise<any>;
}

export default class ConfigService extends PwmService implements IConfigService {
    static $inject = ['$http', '$q'];
    constructor(private $http: IHttpService, private $q: IQService) {
        super();
    }

    getColumnConfiguration(): IPromise<any> {
        return this.$http
            .get(this.getServerUrl('clientData'))
            .then((response) => {
                return this.$q.resolve(response.data['data']['peoplesearch_search_columns']);
            });
    }
}
