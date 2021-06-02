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


import {IHelpDeskService, ISuccessResponse} from '../../services/helpdesk.service';
import {IChangePasswordSuccess} from './success-change-password.controller';

export default class RandomChangePasswordController {

    static $inject = [
        'HelpDeskService',
        'IasDialogService',
        'personUserKey',
        'translateFilter'
    ];
    constructor(private HelpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private personUserKey: string,
                private translateFilter: (id: string) => string) {
    }

    confirmSetRandomPassword() {
        this.HelpDeskService.setPassword(this.personUserKey, true)
            .then((result: ISuccessResponse) => {
                // Send the password and success message to the parent element via the close() method.
                let chosenPassword = '[' + this.translateFilter('Display_Random') +  ']';
                let data: IChangePasswordSuccess = { password: chosenPassword, successMessage: result.successMessage };
                this.IasDialogService.close(data);
            });
    }
}
