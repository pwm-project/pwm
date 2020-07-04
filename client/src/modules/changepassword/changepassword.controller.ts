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

/* tslint:disable */

import {ICompileService, IScope, ITemplateCacheService, element} from 'angular';

declare const PWM_GLOBAL: any;
declare const PWM_MAIN: any;

const PWM_CHANGEPW = window['PWM_CHANGEPW'];
const PW_SUGGESTIONS_TEMPLATE = require("./password-suggestions.html");
require("./password-suggestions.scss");

export default class ChangePasswordController {
    static $inject = ["$scope", "$compile", "$templateCache"];
    constructor(
            private $scope: IScope | any,
            private $compile: ICompileService,
            private $templateCache: ITemplateCacheService
    ) {
    }

    getString(key: string) {
        return PWM_MAIN.showString(key);
    }

    doRandomGeneration() {
        PWM_MAIN.showDialog({
            title: PWM_MAIN.showString('Title_RandomPasswords'),
            dialogClass: 'narrow',
            text: "",
            showOk: false,
            showClose: true,
            loadFunction: () => {
                this.populateDialog()
            }
        });
    }

    populateDialog() {
        this.$scope.$ctrl = this;
        const passwordSuggestionsElement: JQuery = this.$compile(this.$templateCache.get(PW_SUGGESTIONS_TEMPLATE) as string)(this.$scope);

        var myElement = element( document.querySelector( '#dialogPopup .dialogBody, #html5Dialog .dialogBody' ) );
        myElement.replaceWith(passwordSuggestionsElement);

        this.$scope.$applyAsync();

        PWM_CHANGEPW.beginFetchRandoms({});
    }

    onChoosePassword(event) {
        PWM_CHANGEPW.copyToPasswordFields(event.target.textContent);
    }

    onMoreRandomsButtonClick() {
        PWM_CHANGEPW.beginFetchRandoms({});
    }

    onCancelRandomsButtonClick() {
        PWM_MAIN.closeWaitDialog('dialogPopup');
    }
}
