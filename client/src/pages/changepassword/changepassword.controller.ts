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

/* tslint:disable */

import {ICompileService, IScope, ITemplateCacheService, element} from 'angular';

declare const PWM_GLOBAL: any;
declare const PWM_MAIN: any;

const PWM_CHANGEPW = window['PWM_CHANGEPW'];
const PW_SUGGESTIONS_TEMPLATE = require("pages/changepassword/password-suggestions.html");
require("pages/changepassword/password-suggestions.scss");

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
