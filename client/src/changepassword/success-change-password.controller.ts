/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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


import {IHelpDeskService } from '../services/helpdesk.service';
import {IQService} from 'angular';
import {IHelpDeskConfigService} from '../services/helpdesk-config.service';
import DialogService from '../ux/ias-dialog.service';

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

    static $inject = [
        '$q',
        'changePasswordSuccessData',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'personUsername',
        'personUserKey',
        'translateFilter'
    ];
    constructor(private $q: IQService,
                changePasswordSuccessData: IChangePasswordSuccess,
                private configService: IHelpDeskConfigService,
                private HelpDeskService: IHelpDeskService,
                private IasDialogService: DialogService,
                private personUsername: string,
                private personUserKey: string,
                private translateFilter: (id: string) => string) {
        this.password = changePasswordSuccessData.password;
        this.successMessage = changePasswordSuccessData.successMessage;

        let promise = this.$q.all([
            this.configService.getClearResponsesSetting(),
            this.configService.maskPasswordsEnabled()
        ]);
        promise.then((result) => {
            this.clearResponsesSetting = result[0];
            this.maskPasswords = result[1];
            this.passwordMasked = this.maskPasswords;
        });
    }

    clearAnswers() {
        this.IasDialogService.close();
    }

    togglePasswordMasked() {
        this.passwordMasked = !this.passwordMasked;
    }
}
