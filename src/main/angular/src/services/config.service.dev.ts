import { IConfigService } from './config.service';
import { IPromise, IQService } from 'angular';
import PwmService from './pwm.service';

export default class ConfigService extends PwmService implements IConfigService {
    static $inject = ['$q'];
    constructor(private $q: IQService) {
        super();
    }

    getColumnConfiguration(): IPromise<any> {
        return this.$q.resolve({
            givenName: 'First Name',
            sn: 'Last Name',
            title: 'Title',
            mail: 'Email',
            telephoneNumber: 'Telephone'
        });
    }
}
