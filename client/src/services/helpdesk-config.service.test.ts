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
