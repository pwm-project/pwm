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


import { ILogService, IWindowService } from 'angular';

const PWM_PREFIX = 'PWM_';
const KEYS = {
    SEARCH_TEXT: 'searchText',
    HELPDESK_SEARCH_TEXT: 'helpdeskSearchText',
    SEARCH_VIEW: 'searchView',
    HELPDESK_SEARCH_VIEW: 'helpdeskSearchView',
    VERIFICATION_STATE: 'verificationState'
};

export default class LocalStorageService {
    keys: any = KEYS;
    private localStorageEnabled = true;

    static $inject = [ '$log', '$window' ];
    constructor($log: ILogService, private $window: IWindowService) {
        if (!$window.sessionStorage.getItem) {
            this.localStorageEnabled = false;
            $log.info('Local Storage API not enabled. Using NOOP implementation.');
        }
    }

    getItem(key: string): any {
        if (this.localStorageEnabled) {
            return this.$window.sessionStorage[this.prepKey(key)];
        }

        return null;
    }

    setItem(key: string, value: any): void {
        if (this.localStorageEnabled && value) {
            this.$window.sessionStorage[this.prepKey(key)] = value;
        }
    }

    removeItem(key: string): any {
        if (this.localStorageEnabled) {
            return this.$window.sessionStorage.removeItem(this.prepKey(key));
        }
    }

    private prepKey(key: string) {
        return PWM_PREFIX + key;
    }
}
