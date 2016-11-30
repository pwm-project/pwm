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


import { IAugmentedJQuery } from 'angular';
import { Component } from '../component';
import Person from '../models/person.model';
import { IPeopleService } from '../services/people.service';

@Component({
    bindings: {
        directReports: '<',
        disableFocus: '<',
        person: '<',
        showImage: '<',
        size: '@',
        showDirectReportCount: '<'
    },
    stylesheetUrl: require('peoplesearch/person-card.component.scss'),
    templateUrl: require('peoplesearch/person-card.component.html')
})
export default class PersonCardComponent {
    private details: any[]; // For large style cards
    private disableFocus: boolean;
    private person: Person;
    private directReports: Person[];
    private size: string;
    private showDirectReportCount: boolean;
    private showImage: boolean;

    static $inject = ['$element', 'PeopleService'];
    constructor(private $element: IAugmentedJQuery, private peopleService: IPeopleService) {
        this.details = [];
        this.size = 'medium';
    }

    $onInit(): void {
        if (!this.disableFocus) {
            this.$element[0].tabIndex = 0;
            this.$element.on('keydown', this.onKeyDown.bind(this));
        }
    }

    $onChanges(): void {
        if (this.person) {
            this.setDisplayData();

            if (this.showDirectReportCount) {
                this.peopleService.getNumberOfDirectReports(this.person.userKey)
                    .then((numDirectReports) => {
                        this.person.numDirectReports = numDirectReports;
                    })
                    .catch(() => {
                        // TODO: error handling
                    });
            }
        }
    }

    $onDestroy(): void {
        this.$element.off('keydown', this.onKeyDown.bind(this));
    }

    getAvatarStyle(): any {
        if (!this.showImage) {
            return { 'background-image': 'url()' };
        }

        if (this.person && this.person.photoURL) {
            return { 'background-image': 'url(' + this.person.photoURL + ')' };
        }

        return {};
    }

    isSmall(): boolean {
        return this.size === 'small';
    }

    private onKeyDown(event: KeyboardEvent): void {
        if (event.keyCode === 13 || event.keyCode === 32) { // 13 = Enter, 32 = Space
            this.$element.triggerHandler('click');

            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }

    private setDisplayData(): void {
        if (this.person.detail) {
            this.details = Object
                .keys(this.person.detail)
                .map((key: string) => {
                    return this.person.detail[key];
                });
        }

        if (this.directReports) {
            this.person.numDirectReports = this.directReports.length;
        }
    }
}
