import { IConfigService } from './config.service';
import { IPromise, IQService } from 'angular';

export default class ConfigService implements IConfigService {
    static $inject = [ '$q' ];
    constructor(private $q: IQService) {}

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
