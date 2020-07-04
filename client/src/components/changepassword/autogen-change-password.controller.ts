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


import {IHelpDeskService, IRandomPasswordResponse, ISuccessResponse} from '../../services/helpdesk.service';
import {IPromise, IQService} from 'angular';
import {IChangePasswordSuccess} from './success-change-password.controller';

const RANDOM_MAPPING_SIZE = 20;

require('./autogen-change-password.component.scss');

export default class AutogenChangePasswordController {
    fetchingRandoms: boolean;
    passwordSuggestions: string[];

    static $inject = [ '$q', 'HelpDeskService', 'IasDialogService', 'personUserKey' ];
    constructor(private $q: IQService,
                private HelpDeskService: IHelpDeskService,
                private IasDialogService: any,
                private personUserKey: string) {
        this.passwordSuggestions = [];
        for (let i = 0; i < 20; i++) {
            this.passwordSuggestions.push('');
        }
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
