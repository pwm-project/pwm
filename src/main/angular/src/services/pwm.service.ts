import { ILogService, IWindowService } from 'angular';

export default class PwmService {
    PWM_GLOBAL: any;
    PWM_MAIN: any;

    urlContext: string;

    static $inject = [ '$log', '$window' ];
    constructor(private $log: ILogService, $window: IWindowService) {
        this.urlContext = '';

        // Search window references to PWM_GLOBAL and PWM_MAIN add by legacy PWM code
        if ($window['PWM_GLOBAL']) {
            this.PWM_GLOBAL = $window['PWM_GLOBAL'];
            this.urlContext = this.PWM_GLOBAL['url-context'];
        }
        else {
            this.$log.warn('PWM_GLOBAL is not defined on window');
        }

        if ($window['PWM_MAIN']) {
            this.PWM_MAIN = $window['PWM_MAIN'];
        }
        else {
            this.$log.warn('PWM_MAIN is not defined on window');
        }
    }

    getServerUrl(processAction: string, additionalParameters?: any): string {
        let url: string = this.urlContext + '/private/peoplesearch?processAction=' + processAction;
        url = this.addParameters(url, additionalParameters);
        url = this.addPwmFormIdToUrl(url);

        return url;
    }

    get localeStrings(): any {
        if (this.PWM_GLOBAL) {
            return this.PWM_GLOBAL['localeStrings'];
        }

        return {};
    }

    get startupFunctions(): any[] {
        if (this.PWM_GLOBAL) {
            return this.PWM_GLOBAL['startupFunctions'];
        }

        return [];
    }

    private addPwmFormIdToUrl(url: string): string {
        if (!this.PWM_MAIN) {
            return url;
        }

        return this.PWM_MAIN.addPwmFormIDtoURL(url);
    }


    private addParameters(url: string, params: any): string {
        if (!this.PWM_MAIN) {
            return url;
        }

        if (params) {
            for (var name in params) {
                if (params.hasOwnProperty(name)) {
                    url = this.PWM_MAIN.addParamToUrl(url, name, params[name]);
                }
            }
        }

        return url;
    }
}
