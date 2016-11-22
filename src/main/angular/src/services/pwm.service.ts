/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


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
