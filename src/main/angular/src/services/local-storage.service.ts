/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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


import { IWindowService } from 'angular';

const PWM_PREFIX = 'PWM_';
const KEYS = {
    SEARCH_TEXT: 'searchText'
};

export default class LocalStorageService {
    keys: any = KEYS;

    static $inject = [ '$window' ];
    constructor(private $window: IWindowService) {}

    getItem(key: string): any {
        return this.$window.localStorage[this.prepKey(key)];
    }

    setItem(key: string, value: any): void {
        this.$window.localStorage[this.prepKey(key)] = value;
    }

    removeItem(key: string): any {
        return this.$window.localStorage.removeItem(this.prepKey(key));
    }

    private prepKey(key: string) {
        return PWM_PREFIX + key;
    }
}
