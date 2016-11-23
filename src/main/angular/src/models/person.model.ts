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


export default class Person {
    // Common properties
    userKey: string;
    numDirectReports: number;

    // Autocomplete properties (via Search)
    _displayName: string;

    // Details properties (not available in search)
    detail: any;
    displayNames: string[];
    photoURL: string;
    links: any[];

    // Search properties (not available in details)
    givenName: string;
    mail: string;
    sn: string;
    telephoneNumber: string;
    title: string;

    constructor(options: any) {
        // Common properties
        this.userKey = options.userKey;

        // Autocomplete properties (via Search)
        this._displayName = options._displayName;

        // Details properties
        this.detail = options.detail;
        this.displayNames = options.displayNames;
        this.photoURL = options.photoURL;
        this.links = options.links;

        // Search properties
        this.givenName = options.givenName;
        this.mail = options.mail;
        this.sn = options.sn;
        this.telephoneNumber = options.telephoneNumber;
        this.title = options.title;
    }
}
