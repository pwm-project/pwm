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


import {IHelpDeskService, IRandomPasswordResponse, ISuccessResponse} from '../services/helpdesk.service';
import {IPromise, IQService} from 'angular';
import {IHelpDeskConfigService, PASSWORD_UI_MODES} from '../services/helpdesk-config.service';
import DialogService from '../ux/ias-dialog.service';

const RANDOM_MAPPING_SIZE = 20;
const STATUS_AUTOGEN = 'autogen';
const STATUS_CONFIRM_RANDOM = 'confirm-random';
const STATUS_FINISHED = 'finished';
const STATUS_TYPE = 'type';

require('helpdesk/password-suggestions-dialog.scss');

export default class PasswordSuggestionsDialogController {
    chosenPassword: string;
    clearResponsesSetting: string;
    fetchingRandoms: boolean;
    maskPasswords: boolean;
    passwordMasked: boolean;
    passwordSuggestions: string[];
    passwordUiMode: string;
    status: string;
    successMessage: string;
    // this.HelpDeskService.showStrengthMeter;

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

        let promise = this.$q.all([
            this.configService.getClearResponsesSetting(),
            this.configService.getPasswordUiMode(),
            this.configService.maskPasswordsEnabled()
        ]);
        promise.then((result) => {
            this.clearResponsesSetting = result[0];
            this.passwordUiMode = result[1];
            this.maskPasswords = result[2];
            this.passwordMasked = this.maskPasswords;   // Set now instead of when we set status to STATUS_FINISHED
            if (this.passwordUiMode === PASSWORD_UI_MODES.AUTOGEN) {
                this.status = STATUS_AUTOGEN;
                this.populatePasswordSuggestions();
            }
            else if (this.passwordUiMode === PASSWORD_UI_MODES.RANDOM) {
                this.status = STATUS_CONFIRM_RANDOM;
            }
            else if (this.passwordUiMode === PASSWORD_UI_MODES.BOTH || this.passwordUiMode === PASSWORD_UI_MODES.TYPE) {
                this.status = STATUS_TYPE;
            }
            else {
                throw new Error('Password type unsupported!');  // TODO: best way to do this?
            }
        });
    }

    chooseTypedPassword() {     // todo: should this be merged with onChoosePasswordSuggestion?
        this.HelpDeskService.setPassword(this.personUserKey, false, this.chosenPassword)
            .then((result: ISuccessResponse) => {
                this.status = STATUS_FINISHED;
                this.successMessage = result.successMessage;
            });
    }

    clearAnswers() {
        this.IasDialogService.close();
    }

    confirmSetRandomPassword() {
        this.HelpDeskService.setPassword(this.personUserKey, true)
            .then((result: ISuccessResponse) => {
                this.chosenPassword = '[' + this.translateFilter('Display_Random') +  ']';
                this.status = STATUS_FINISHED;
                this.successMessage = result.successMessage;
            });
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
        this.chosenPassword = this.passwordSuggestions[index];
        this.HelpDeskService.setPassword(this.personUserKey, false, this.chosenPassword)
            .then((result: ISuccessResponse) => {
                this.status = STATUS_FINISHED;
                this.successMessage = result.successMessage;
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

    togglePasswordMasked() {
        this.passwordMasked = !this.passwordMasked;
    }
}
