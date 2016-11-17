import 'angular-translate';
import { IQService } from 'angular';
import PwmService from './pwm.service';

export default [
    '$q',
    'PwmService',
    ($q: IQService, pwmService: PwmService) => {
        return function () {
            var deferred = $q.defer();

            pwmService.startupFunctions.push(() => {
                deferred.resolve(pwmService.localeStrings['Display']);
            });

            // resolve with translation data
            return deferred.promise;
        };
    }];
