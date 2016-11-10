// These come from legacy PWM:
declare var PWM_GLOBAL: any;
declare var PWM_MAIN: any;

export default class PwmService {
    protected getServerUrl(processAction: string, additionalParameters?: any): string {
        let url: string = PWM_GLOBAL['url-context'] + '/private/peoplesearch?processAction=' + processAction;
        url = PwmService.addParameters(url, additionalParameters);
        url = PWM_MAIN.addPwmFormIDtoURL(url);

        return url;
    }

    private static addParameters(url: string, params: any): string {
        if (params) {
            for (var name in params) {
                if (params.hasOwnProperty(name)) {
                    url = PWM_MAIN.addParamToUrl(url, name, params[name]);
                }
            }
        }

        return url;
    }
}
