import { IQService } from 'angular';
import 'angular-translate';

declare var PWM_GLOBAL: any;

export default ['$q', ($q: IQService) => {
    // return loaderFn
    return function (options) {
        var deferred = $q.defer();

        PWM_GLOBAL['startupFunctions'].push(() => {
            deferred.resolve(PWM_GLOBAL['localeStrings']['Display']);
        });

        // resolve with translation data
        return deferred.promise;
    };
}];
