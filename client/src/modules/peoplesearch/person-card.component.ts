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


import {IAugmentedJQuery} from 'angular';
import { IPerson } from '../../models/person.model';
import { IPeopleService } from '../../services/people.service';

const templateUrl = require('./person-card.component.html');

class PersonCardController {
    private details: any[]; // For large style cards
    private disableFocus: boolean;
    private person: IPerson;
    private directReports: IPerson[];
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
                    .then(
                        (numDirectReports) => {
                            this.person.numDirectReports = numDirectReports;
                        },
                        (error) => {
                            // TODO: handle error. NOOP is fine for now because it won't try to display the result
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

    get numDirectReportsVisible(): boolean {
        return this.showDirectReportCount && this.person && !!this.person.numDirectReports;
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

export default function PersonCardDirectiveFactory() {
    return {
        bindToController: true,
        controller: PersonCardController,
        controllerAs: '$ctrl',
        restrict: 'E',
        replace: true,
        scope: {
            directReports: '<',
            disableFocus: '<',
            person: '<',
            showImage: '<',
            size: '@',
            showDirectReportCount: '<'
        },
        templateUrl: templateUrl
    };
}
