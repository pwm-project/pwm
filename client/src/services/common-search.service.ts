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

import LocalStorageService from './local-storage.service';
import {IAdvancedSearchQuery, IAttributeMetadata} from './base-config.service';

const PS_ADV_SEARCH_ACTIVE = 'psAdvancedSearchActive';
const PS_ADV_SEARCH_QUERIES = 'psAdvancedSearchQueries';
const HD_ADV_SEARCH_ACTIVE = 'hdAdvancedSearchActive';
const HD_ADV_SEARCH_QUERIES = 'hdAdvancedSearchQueries';

export default class CommonSearchService {
    static $inject = ['LocalStorageService'];
    constructor(private localStorageService: LocalStorageService) {
    }

    getDefaultValue(attributeMetaData: IAttributeMetadata) {
        if (attributeMetaData) {
            if (attributeMetaData.type === 'select') {
                const keys: string[] = Object.keys(attributeMetaData.options);
                if (keys && keys.length > 0) {
                    return keys[0];
                }
            }
        }

        return '';
    }

    isPsAdvancedSearchActive(): boolean {
        return this.isAdvSearchActive(PS_ADV_SEARCH_ACTIVE);
    }

    setPsAdvancedSearchActive(active: boolean): void {
        this.setAdvSearchActive(PS_ADV_SEARCH_ACTIVE, active);
    }

    getPsAdvSearchQueries(): IAdvancedSearchQuery[] {
        return this.getAdvSearchQueries(PS_ADV_SEARCH_QUERIES);
    }

    setPsAdvSearchQueries(queries: IAdvancedSearchQuery[]) {
        this.setAdvSearchQueries(PS_ADV_SEARCH_QUERIES, queries);
    }

    isHdAdvancedSearchActive(): boolean {
        return this.isAdvSearchActive(HD_ADV_SEARCH_ACTIVE);
    }

    setHdAdvancedSearchActive(active: boolean): void {
        this.setAdvSearchActive(HD_ADV_SEARCH_ACTIVE, active);
    }

    getHdAdvSearchQueries(): IAdvancedSearchQuery[] {
        return this.getAdvSearchQueries(HD_ADV_SEARCH_QUERIES);
    }

    setHdAdvSearchQueries(queries: IAdvancedSearchQuery[]) {
        this.setAdvSearchQueries(HD_ADV_SEARCH_QUERIES, queries);
    }

    isAdvSearchActive(storageName: string): boolean {
        if (storageName) {
            const storageValue = this.localStorageService.getItem(storageName);
            if (storageValue) {
                return (storageValue === 'true');
            }
        }

        return false;
    }

    setAdvSearchActive(storageName: string, active: boolean): void {
        if (storageName) {
            // Make sure active is a boolean first
            if (typeof(active) === typeof(true)) {
                this.localStorageService.setItem(storageName, JSON.stringify(active));
            } else {
                // If we were given undefine or null data, then just remove the named item from local storage
                this.localStorageService.removeItem(storageName);
            }
        }
    }

    getAdvSearchQueries(storageName: string): IAdvancedSearchQuery[] {
        if (storageName) {
            const storageValue = this.localStorageService.getItem(storageName);
            if (storageValue) {
                try {
                    const parsedValue = JSON.parse(storageValue);
                    if (Array.isArray(parsedValue)) {
                        return parsedValue;
                    }
                } catch (error) {
                    // Unparseable, an empty array will be returned below
                }
            }
        }

        return [];
    }

    setAdvSearchQueries(storageName: string, queries: IAdvancedSearchQuery[]) {
        if (storageName) {
            if (queries) {
                this.localStorageService.setItem(storageName, JSON.stringify(queries));
            } else {
                // If we were given undefine or null data, then just remove the named item from local storage
                this.localStorageService.removeItem(storageName);
            }
        }
    }
}
