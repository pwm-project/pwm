/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import {IHttpService, ILogService, IPromise, IQService, IWindowService} from 'angular';

export interface IHttpRequestOptions {
    data?: any;
    preventCache?: boolean;
}

export interface IPwmService {
    getServerUrl(processAction: string, additionalParameters?: any): string;
    httpRequest<T>(url: string, options: IHttpRequestOptions): IPromise<T>;
    ajaxTypingWait: number;
    localeStrings: any;
    startupFunctions: any[];
}

const DEFAULT_AJAX_TYPING_WAIT = 700;

export default class PwmService implements IPwmService {
    PWM_GLOBAL: any;
    PWM_MAIN: any;

    urlContext: string;

    static $inject = [ '$http', '$log', '$q', '$window' ];
    constructor(private $http: IHttpService,
                private $log: ILogService,
                private $q: IQService,
                $window: IWindowService) {
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
        let url: string = window.location.pathname + '?processAction=' + processAction;
        url = this.addParameters(url, additionalParameters);

        return url;
    }

    private handlePwmError(response): IPromise<any> {
        // TODO: show error dialog (like PWM_MAIN.ajaxRequest)
        const errorMessage = `${response.data['errorCode']}: ${response.data['errorMessage']}`;
        this.$log.error(errorMessage);

        return this.$q.reject(response.data['errorMessage']);
    }

    httpRequest<T>(url: string, options: IHttpRequestOptions): IPromise<T> {
        // TODO: implement alternate http method, no Content-Type if no options.data
        let formID: string = encodeURIComponent('&pwmFormID=' + this.PWM_GLOBAL['pwmFormID']);
        url += '&pwmFormID=' + this.PWM_GLOBAL['pwmFormID'];
        let promise = this.$http
            .post(url, options.data, {
                cache: !options.preventCache,
                headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            })
            .then((response) => {
                if (response.data['error']) {
                    return this.handlePwmError(response);
                }

                // Note: sometimes response.data looks like this:
                // {
                //     "error": false,
                //     "errorCode": 0,
                //     "data": {
                //         "foo": "1",
                //         "bar": "2"
                //     }
                // }

                // Note: other times, response.data looks like this:
                // {
                //     "error": false,
                //     "errorCode": 0,
                //     "successMessage": "The operation has been successfully completed."
                // }

                // Since we can't make assumptions about the structure, we just need to return the whole response.data
                // payload:
                return <T>response.data;
            });

        return promise;
    }

    get ajaxTypingWait(): number {
        if (this.PWM_GLOBAL) {
            return this.PWM_GLOBAL['client.ajaxTypingWait'] || DEFAULT_AJAX_TYPING_WAIT;
        }

        return DEFAULT_AJAX_TYPING_WAIT;
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

    private addParameters(url: string, params: any): string {
        if (!this.PWM_MAIN) {
            return url;
        }

        if (params) {
            for (let name in params) {
                if (params.hasOwnProperty(name)) {
                    url = this.PWM_MAIN.addParamToUrl(url, name, params[name]);
                }
            }
        }

        return url;
    }
}
