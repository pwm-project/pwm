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


import {IHelpDeskService, ISuccessResponse} from '../services/helpdesk.service';
import {IQService} from 'angular';
import {IHelpDeskConfigService} from '../services/helpdesk-config.service';
import DialogService from '../ux/ias-dialog.service';
import {IChangePasswordSuccess} from './success-change-password.controller';

require('changepassword/type-change-password.component.scss');

export default class TypeChangePasswordController {
    chosenPassword: string;
    maskPasswords: boolean;
    message: string;
    password1: string;
    password2: string;
    password1Masked: boolean;
    password2Masked: boolean;
    passwordSuggestions: string[];
    showStrengthMeter: boolean;
    strength: string = 'Very Strong';

    static $inject = [
        '$q',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'personUsername',
        'personUserKey',
        'translateFilter'
    ];
    constructor(private $q: IQService,
                private configService: IHelpDeskConfigService,
                private HelpDeskService: IHelpDeskService,
                private IasDialogService: DialogService,
                private personUsername: string,
                private personUserKey: string,
                private translateFilter: (id: string) => string) {
        this.passwordSuggestions = Array(20).fill('');
        this.message = translateFilter('Display_PasswordPrompt');
        this.showStrengthMeter = HelpDeskService.showStrengthMeter;

        this.configService.maskPasswordsEnabled()
            .then((maskPasswords: boolean) => {
                this.maskPasswords = maskPasswords;
                this.password1Masked = this.maskPasswords;
                this.password2Masked = this.maskPasswords;
            });
    }

    chooseTypedPassword() {     // todo: should this be merged with onChoosePasswordSuggestion?
        this.HelpDeskService.setPassword(this.personUserKey, false, this.chosenPassword)
            .then((result: ISuccessResponse) => {
                // Send the password and success message to the parent element via the close() method.
                let data: IChangePasswordSuccess = { password: this.password1, successMessage: result.successMessage };
                this.IasDialogService.close(data);
            });
    }

    // Use the autogenPasswords property to signify to the parent element that the operator clicked "Random Passwords"
    onClickRandomPasswords() {
        this.IasDialogService.close({ autogenPasswords: true });
    }

    togglePassword1Masked() {
        this.password1Masked = !this.password1Masked;
    }

    togglePassword2Masked() {
        this.password2Masked = !this.password2Masked;
    }
}
