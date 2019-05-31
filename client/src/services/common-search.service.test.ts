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


import {IHttpService, ILogService, IQService, IWindowService, module} from 'angular';
import LocalStorageService from './local-storage.service';
import CommonSearchService from './common-search.service';
import {anyNumber, anyString, deepEqual, instance, mock, strictEqual, verify, when} from 'ts-mockito';
import {IAdvancedSearchQuery} from './base-config.service';

describe('In common-search.service.test.ts', () => {
    beforeEach(() => {
        module('app', []);
    });

    // Define some angular objects we'll grab from angular-mocks
    let $log: ILogService;

    beforeEach(inject((_$http_, _$log_, _$q_, _$window_) => {
        $log = _$log_;
        $log.info('This is an info message');
    }));

    it('Pulls search queries from local storage, or empty array if undefined or bad data', (done: DoneFn) => {
        let mockLocalStorageService = mock(LocalStorageService);
        when(mockLocalStorageService.getItem('undefinedScenario')).thenReturn(undefined);
        when(mockLocalStorageService.getItem('bogusScenario')).thenReturn('bogus');
        when(mockLocalStorageService.getItem('notArrayScenario')).thenReturn('{"key":"foo","value":"bar"}');
        when(mockLocalStorageService.getItem('goodScenario')).thenReturn(JSON.stringify([
            {
                key: 'foo',
                value: 'bar'
            }
        ]));

        const commonSearchService = new CommonSearchService(instance(mockLocalStorageService));

        expect(commonSearchService.getAdvSearchQueries('undefinedScenario')).toEqual([]);
        expect(commonSearchService.getAdvSearchQueries('bogusScenario')).toEqual([]);
        expect(commonSearchService.getAdvSearchQueries('notArrayScenario')).toEqual([]);
        expect(commonSearchService.getAdvSearchQueries('goodScenario')).toContain({
            key: 'foo',
            value: 'bar'
        });

        done();
    });

    it('Stores search queries into local storage, or does nothing if bad data', (done: DoneFn) => {
        let mockLocalStorageService = mock(LocalStorageService);

        const queries: IAdvancedSearchQuery[] = [
            {key: 'foo', value: 'one'},
            {key: 'bar', value: 'two'},
            {key: 'baz', value: 'three'}
        ];

        const commonSearchService = new CommonSearchService(instance(mockLocalStorageService));

        commonSearchService.setAdvSearchQueries('nullData', null);
        commonSearchService.setAdvSearchQueries('undefinedData', undefined);
        commonSearchService.setAdvSearchQueries('emptyArray', []);
        commonSearchService.setAdvSearchQueries('lotsOfData', queries);

        verify(mockLocalStorageService.removeItem('nullData')).called();
        verify(mockLocalStorageService.removeItem('undefinedData')).called();
        verify(mockLocalStorageService.setItem('emptyArray', '[]')).called();
        verify(mockLocalStorageService.setItem('emptyArray', anyString())).called();

        done();
    });

    it('Pulls advanced search active state from local storage, or false if undefined or bad data', (done: DoneFn) => {
        let mockLocalStorageService = mock(LocalStorageService);
        when(mockLocalStorageService.getItem('undefinedScenario')).thenReturn(undefined);
        when(mockLocalStorageService.getItem('bogusScenario')).thenReturn('bogus');
        when(mockLocalStorageService.getItem('invalidScenario')).thenReturn('{}');
        when(mockLocalStorageService.getItem('falseScenario')).thenReturn(JSON.stringify(false));
        when(mockLocalStorageService.getItem('trueScenario')).thenReturn(JSON.stringify(true));

        const commonSearchService = new CommonSearchService(instance(mockLocalStorageService));

        expect(commonSearchService.isAdvSearchActive('undefinedScenario')).toEqual(false);
        expect(commonSearchService.isAdvSearchActive('bogusScenario')).toEqual(false);
        expect(commonSearchService.isAdvSearchActive('invalidScenario')).toEqual(false);
        expect(commonSearchService.isAdvSearchActive('falseScenario')).toEqual(false);
        expect(commonSearchService.isAdvSearchActive('trueScenario')).toEqual(true);

        done();
    });

    it('Stores the advanced search active state to local storage', (done: DoneFn) => {
        let mockLocalStorageService = mock(LocalStorageService);

        const commonSearchService = new CommonSearchService(instance(mockLocalStorageService));

        commonSearchService.setAdvSearchActive('nullData', null);
        commonSearchService.setAdvSearchActive('undefinedData', undefined);
        commonSearchService.setAdvSearchActive('trueScenario', true);
        commonSearchService.setAdvSearchActive('falseScenario', false);

        verify(mockLocalStorageService.removeItem('nullData')).called();
        verify(mockLocalStorageService.removeItem('undefinedData')).called();
        verify(mockLocalStorageService.setItem('trueScenario', 'true')).called();
        verify(mockLocalStorageService.setItem('falseScenario', 'false')).called();

        done();
    });
});
