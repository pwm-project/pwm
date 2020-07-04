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

import {IHttpBackendService, IHttpService, ILogService, IQService, IWindowService, module} from 'angular';
import ObjectService from './object.service';
import {default as PwmService, IPwmService} from './pwm.service';
import LocalStorageService from './local-storage.service';
import HelpDeskConfigService, {IVerificationMap} from './helpdesk-config.service';

import {helpdeskProcessAction_clientData} from './helpdesk-config.service.test-data';

describe('In helpdesk-config.service.test.ts', () => {
    beforeEach(() => {
        module('app', []);
    });

    let localStorageService: LocalStorageService;
    let objectService: ObjectService;
    let pwmService: IPwmService;
    let helpDeskConfigService: HelpDeskConfigService;
    let $httpBackend: IHttpBackendService;

    beforeEach(inject((
        $http: IHttpService,
        $log: ILogService,
        $q: IQService,
        $window: IWindowService,
        _$httpBackend_: IHttpBackendService
    ) => {
        localStorageService = new LocalStorageService($log, $window);
        objectService = new ObjectService();
        pwmService = new PwmService($http, $log, $q, $window);
        $httpBackend = _$httpBackend_;
        helpDeskConfigService = new HelpDeskConfigService($http, $log, $q, pwmService as PwmService);
    }));

    it('getVerificationMethods returns only the required verification methods', (done: DoneFn) => {
        $httpBackend.whenGET( '/context.html?processAction=clientData').respond(helpdeskProcessAction_clientData);

        helpDeskConfigService.getVerificationMethods()
            .then((verifications: IVerificationMap) => {
                expect(verifications.length).toBe(2);

                expect(verifications).toContain({name: 'ATTRIBUTES', label: 'Button_Attributes'});
                expect(verifications).toContain({name: 'OTP', label: 'Button_OTP'});

                done();
            })
            .catch((error: Error) => {
                done.fail(error);
            });

        // This causes the $http service to finally resolve the response:
        $httpBackend.flush();
    });

    // helpDeskConfigService should return both required and optional, because we passed in: {includeOptional: true}
    it('getVerificationMethods returns both required and optional verification methods', (done: DoneFn) => {
        $httpBackend.whenGET( '/context.html?processAction=clientData').respond(helpdeskProcessAction_clientData);

        helpDeskConfigService.getVerificationMethods({includeOptional: true})
            .then((verifications: IVerificationMap) => {
                expect(verifications.length).toBe(3);

                expect(verifications).toContain({name: 'ATTRIBUTES', label: 'Button_Attributes'});
                expect(verifications).toContain({name: 'EMAIL', label: 'Button_Email'});
                expect(verifications).toContain({name: 'OTP', label: 'Button_OTP'});

                done();
            })
            .catch((error: Error) => {
                done.fail(error);
            });

        // This causes the $http service to finally resolve the response:
        $httpBackend.flush();
    });
});
