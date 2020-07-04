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

import HelpDeskService, {IRecentVerifications} from './helpdesk.service';
import {IHttpBackendService, IHttpService, ILogService, IQService, IWindowService, module} from 'angular';
import ObjectService from './object.service';
import {default as PwmService, IPwmService} from './pwm.service';
import LocalStorageService from './local-storage.service';
import {getRecentVerifications_response} from './helpdesk.service.test-data';

describe('In helpdesk.service.test.ts', () => {
    beforeEach(() => {
        module('app', []);
    });

    let localStorageService: LocalStorageService;
    let objectService: ObjectService;
    let pwmService: IPwmService;
    let helpDeskService: HelpDeskService;
    let $httpBackend: IHttpBackendService;
    let $window: IWindowService;

    beforeEach(inject((
        $http: IHttpService,
        $log: ILogService,
        $q: IQService,
        _$window_: IWindowService,
        _$httpBackend_: IHttpBackendService
    ) => {
        $httpBackend = _$httpBackend_;
        $window = _$window_;

        localStorageService = new LocalStorageService($log, $window);
        objectService = new ObjectService();
        pwmService = new PwmService($http, $log, $q, $window);
        helpDeskService = new HelpDeskService($log, $q, localStorageService, objectService, pwmService, $window);
    }));

    it('getRecentVerifications returns the right record data', (done: DoneFn) => {
        (pwmService as PwmService).PWM_GLOBAL = { pwmFormID: 'fake-pwm-form-id' };

        $httpBackend.whenPOST( '/context.html?processAction=showVerifications&pwmFormID=fake-pwm-form-id')
            .respond(getRecentVerifications_response);

        helpDeskService.getRecentVerifications()
            .then((recentVerifications: IRecentVerifications) => {
                expect(recentVerifications.length).toBe(1);

                expect(recentVerifications[0].username).toBe('bjenner');
                expect(recentVerifications[0].profile).toBe('default');
                expect(recentVerifications[0].timestamp).toBe('2018-02-22T15:14:39Z');
                expect(recentVerifications[0].method).toBe('Personal Data');

                done();
            })
            .catch((error: Error) => {
                done.fail(error);
            });

        // This causes the $http service to finally resolve the response:
        $httpBackend.flush();
    });
});
