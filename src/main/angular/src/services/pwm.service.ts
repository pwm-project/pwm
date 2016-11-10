// These come from legacy PWM:
declare var PWM_GLOBAL: any;
declare var PWM_MAIN: any;

export default class PwmService {
    protected getServerUrl(processAction: string): string {
        let url: string = PWM_GLOBAL['url-context'] + '/private/peoplesearch?processAction=' + processAction;
        url = PWM_MAIN.addPwmFormIDtoURL(url);
        return url;
    }
}
