/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
