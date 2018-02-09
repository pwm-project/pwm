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

import HelpDeskService from './helpdesk.service';
import {IHttpService, ILogService, IQService, IWindowService, module} from 'angular';
import ObjectService from './object.service';
import {default as PwmService, IPwmService} from './pwm.service';
import LocalStorageService from './local-storage.service';

describe('In helpdesk.service.test.ts', () => {
    beforeEach(() => {
        module('app', []);
    });

    let localStorageService: LocalStorageService;
    let objectService: ObjectService;
    let pwmService: IPwmService;
    let helpDeskService: HelpDeskService;

    beforeEach(inject(($http: IHttpService, $log: ILogService, $q: IQService, $window: IWindowService) => {
        localStorageService = new LocalStorageService($log, $window);
        objectService = new ObjectService();
        pwmService = new PwmService($http, $log, $q, $window);
        helpDeskService = new HelpDeskService($log, $q, localStorageService, objectService, pwmService, $window);
    }));

    it('a person can be obtained from the HelpDeskService', (done: DoneFn) => {
        expect(helpDeskService).toBeDefined();

        // TODO: need to come back and build up some tests...
        helpDeskService.getPerson('123')
            .then((result: any) => {
                expect(result).toBeDefined();

                done();
            })
            .catch((error: Error) => {
                done.fail(error);
            });
    });
});
