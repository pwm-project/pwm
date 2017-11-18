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


import {IScope, ITimeoutService} from 'angular';
import {IHelpDeskConfigService, VERIFICATION_METHOD_LABELS} from '../services/config-helpdesk.service';
import {IHelpDeskService} from '../services/helpdesk.service';
import DialogService from '../ux/ias-dialog.service';
import {IPerson} from '../models/person.model';

export default class VerificationsDialogController {
    static $inject = [
        '$scope',
        '$state',
        '$timeout',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'person'];
    constructor($scope: IScope,
                $state: angular.ui.IStateService,
                $timeout: ITimeoutService,
                configService: IHelpDeskConfigService,
                helpDeskService: IHelpDeskService,
                IasDialogService: DialogService,
                person: IPerson) {
        $scope.status = 'wait';
        helpDeskService
            .checkVerification(person.userKey)
            .then((response) => {
                if (response.passed) {
                    $timeout(() => {
                        IasDialogService.close();
                        $state.go('details', { personId: person.userKey });
                    });
                }
                else {
                    configService
                        .getVerificationMethods()   // TODO: SMS/OTP/email don't have forms
                        .then((response) => {
                            $scope.status = 'select';
                            $scope.methods = response.required.map((method: string) => {
                                return {
                                    name: method,   // TODO: TOKEN can be sms or email...
                                    label: VERIFICATION_METHOD_LABELS[method]
                                };
                            });
                        });
                    $scope.select = (method: string) => {
                        // check which method we are using
                        // assume attributes for now
                        $scope.status = 'verify';
                        configService.getVerificationForm()
                            .then((response) => {
                                $scope.inputs = response;
                            });
                    };
                    $scope.verify = () => {
                        // check verification
                    };
                }
            });
    }
}
