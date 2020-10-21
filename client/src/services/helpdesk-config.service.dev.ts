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


import { IPromise, IQService } from 'angular';
import {ConfigBaseService} from './base-config.service.dev';
import {IConfigService} from './base-config.service';
import {
    IActionButton,
    IHelpDeskConfigService, IVerificationMap, PASSWORD_UI_MODES, TOKEN_CHOICE, VERIFICATION_METHOD_LABELS,
    VERIFICATION_METHOD_NAMES
} from './helpdesk-config.service';

export default class HelpDeskConfigService extends ConfigBaseService implements IConfigService, IHelpDeskConfigService {
    static $inject = [ '$q' ];
    constructor($q: IQService) {
        super($q);
    }

    getClearResponsesSetting(): IPromise<string> {
        return this.$q.resolve('ask');
    }

    getColumnConfig(): IPromise<any> {
        return this.$q.resolve({
            givenName: 'First Name',
            sn: 'Last Name',
            title: 'Title',
            mail: 'Email',
            telephoneNumber: 'Telephone',
            workforceId: 'Workforce ID'
        });
    }

    getCustomButtons(): IPromise<{[key: string]: IActionButton}> {
        return this.$q.resolve({
            'Clone User': {
                name: 'Clone User',
                description: 'Clones the current user'
            },
            'Merge User': {
                name: 'Merge User',
                description: 'Merges the current user with another user'
            }
        });
    }

    getPasswordUiMode(): IPromise<string> {
        return this.$q.resolve(PASSWORD_UI_MODES.BOTH);
    }

    getTokenSendMethod(): IPromise<string> {
        return this.$q.resolve(TOKEN_CHOICE);
    }

    getVerificationAttributes(): IPromise<IVerificationMap> {
        return this.$q.resolve([
            { name: 'workforceID', label: 'Workforce ID' },
            { name: 'mail', label: 'Email Address' }
        ]);
    }

    getVerificationMethods(): IPromise<IVerificationMap> {
        return this.$q.resolve([
            { name: VERIFICATION_METHOD_NAMES.ATTRIBUTES, label: VERIFICATION_METHOD_LABELS.ATTRIBUTES },
            { name: VERIFICATION_METHOD_NAMES.SMS, label: VERIFICATION_METHOD_LABELS.SMS }
        ]);
    }

    maskPasswordsEnabled(): IPromise<boolean> {
        return this.$q.resolve(true);
    }

    verificationsEnabled(): IPromise<boolean> {
        return this.$q.resolve(true);
    }
}
