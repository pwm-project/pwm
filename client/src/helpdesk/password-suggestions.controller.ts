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


import {IHelpDeskService, IRandomPasswordResponse} from '../services/helpdesk.service';
import {IPromise, IQService} from 'angular';

let RANDOM_MAPPING_LENGTH = 20;

require('helpdesk/password-suggestions-dialog.scss');

export default class PasswordSuggestionsDialogController {
    fetchingRandoms: boolean;
    passwordSuggestions: string[];

    static $inject = [ '$q', 'HelpDeskService', 'personUserKey' ];
    constructor(private $q: IQService, private HelpDeskService: IHelpDeskService, private personUserKey: string) {
        this.passwordSuggestions = Array(20).fill('');
        this.fetchRandoms();
    }

    onChoosePassword(index: number) {
        let password = this.passwordSuggestions[index];
        // change password
    }

    onMoreRandomsButtonClick() {
        this.fetchRandoms();
    }

    fetchRandoms() {
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

    generateRandomMapping(): number[] {
        let map: number[] = [];
        for (let i = 0; i < RANDOM_MAPPING_LENGTH; i++) {
            map.push(i);
        }
        let randomComparatorFunction = () => 0.5 - Math.random();
        map.sort(randomComparatorFunction);
        map.sort(randomComparatorFunction);
        return map;
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
}
