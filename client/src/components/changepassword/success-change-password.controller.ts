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


import {IHelpDeskService } from '../../services/helpdesk.service';
import {IQService} from 'angular';
import {IHelpDeskConfigService, PASSWORD_UI_MODES} from '../../services/helpdesk-config.service';

export interface IChangePasswordSuccess {
    password: string;
    successMessage: string;
}

export default class SuccessChangePasswordController {
    clearResponsesSetting: string;
    maskPasswords: boolean;
    password: string;
    passwordMasked: boolean;
    successMessage: string;
    displayNewPassword: boolean;

    static $inject = [
        '$q',
        'changePasswordSuccessData',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'personUserKey',
        'translateFilter'
    ];
    constructor(private $q: IQService,
                changePasswordSuccessData: IChangePasswordSuccess,
                private configService: IHelpDeskConfigService,
                private HelpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private personUserKey: string,
                private translateFilter: (id: string) => string) {
        this.password = changePasswordSuccessData.password;
        this.successMessage = changePasswordSuccessData.successMessage;

        let promise = this.$q.all([
            this.configService.getClearResponsesSetting(),
            this.configService.maskPasswordsEnabled(),
            this.configService.getPasswordUiMode()
        ]);
        promise.then((result) => {
            this.clearResponsesSetting = result[0];
            this.maskPasswords = result[1];
            this.passwordMasked = this.maskPasswords;

            // If it's random, don't display the new password
            this.displayNewPassword = (result[2] !== PASSWORD_UI_MODES.RANDOM);
        });
    }

    clearAnswers() {
        this.IasDialogService.close();
    }

    togglePasswordMasked() {
        this.passwordMasked = !this.passwordMasked;
    }
}
