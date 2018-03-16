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


import {IHelpDeskService, IRandomPasswordResponse, ISuccessResponse} from '../../services/helpdesk.service';
import {IPromise, IQService} from 'angular';
import {IChangePasswordSuccess} from './success-change-password.controller';

const RANDOM_MAPPING_SIZE = 20;

require('components/changepassword/autogen-change-password.component.scss');

export default class AutogenChangePasswordController {
    fetchingRandoms: boolean;
    passwordSuggestions: string[];

    static $inject = [ '$q', 'HelpDeskService', 'IasDialogService', 'personUserKey' ];
    constructor(private $q: IQService,
                private HelpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private personUserKey: string) {
        this.passwordSuggestions = Array<string>(20).fill('');
        this.populatePasswordSuggestions();
    }

    generateRandomMapping(): number[] {
        let map: number[] = [];
        for (let i = 0; i < RANDOM_MAPPING_SIZE; i++) {
            map.push(i);
        }
        let randomComparatorFunction = () => 0.5 - Math.random();
        map.sort(randomComparatorFunction);
        map.sort(randomComparatorFunction);
        return map;
    }

    onChoosePasswordSuggestion(index: number) {
        let chosenPassword = this.passwordSuggestions[index];
        this.HelpDeskService.setPassword(this.personUserKey, false, chosenPassword)
            .then((result: ISuccessResponse) => {
                // Send the password and success message to the parent element via the close() method.
                let data: IChangePasswordSuccess = { password: chosenPassword, successMessage: result.successMessage };
                this.IasDialogService.close(data);
            });
    }

    passwordSuggestionFactory(index: number): any {
        return () => {
            return this.HelpDeskService.getRandomPassword(this.personUserKey).then(
                (result: IRandomPasswordResponse) => {
                    this.passwordSuggestions[index] = result.password;
                }
            );
        };
    }

    populatePasswordSuggestions() {
        this.fetchingRandoms = true;
        let ordering = this.generateRandomMapping();
        let promiseChain: IPromise<any> = this.$q.when();
        ordering.forEach((index: number) => {
            promiseChain = promiseChain.then(this.passwordSuggestionFactory(index));
        });
        promiseChain.then(() => {
            this.fetchingRandoms = false;
        });
    }
}
