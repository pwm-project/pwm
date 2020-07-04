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


import { Component } from '../../component';
import { element, IAugmentedJQuery, IFilterService, IScope, IWindowService } from 'angular';
import ElementSizeService from '../../ux/element-size.service';
import { IPerson } from '../../models/person.model';
import {IPeopleSearchConfigService} from '../../services/peoplesearch-config.service';

export enum OrgChartSize {
    ExtraSmall = 0,
    Small = 365,
    Large = 631
}

@Component({
    bindings: {
        directReports: '<',
        managementChain: '<',
        assistant: '<',
        person: '<',
        showImages: '<'
    },
    stylesheetUrl: require('./orgchart.component.scss'),
    templateUrl: require('./orgchart.component.html')
})
export default class OrgChartComponent {
    directReports: IPerson[];
    elementWidth: number;
    isLargeLayout: boolean;
    managementChain: IPerson[];
    person: IPerson;
    showDirectReports: boolean;
    assistant: IPerson;

    private elementSize: OrgChartSize = OrgChartSize.ExtraSmall;
    private maxVisibleManagers: number;
    private visibleManagers: IPerson[];

    static $inject = [ '$element', '$filter', '$scope', '$state', '$window', 'ConfigService', 'MfElementSizeService' ];
    constructor(
        private $element: IAugmentedJQuery,
        private $filter: IFilterService,
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $window: IWindowService,
        private configService: IPeopleSearchConfigService,
        private elementSizeService: ElementSizeService) {
    }

    $onDestroy(): void {
        // TODO: remove $window click listener
    }

    $onInit(): void {
        this.configService.orgChartShowChildCount().then(
            (showChildCount: boolean) => {
                this.showDirectReports = showChildCount;
            });

        // OrgChartComponent has different functionality at different widths. On element resize, we
        // want to update the state of the component and trigger a $digest
        this.elementSizeService
            .watchWidth(this.$element, OrgChartSize)
            .onResize(this.onResize.bind(this));

        // In large displays managers are displayed in a row. Any time this property changes, we want
        // to force our manager list to be recalculated in this.getManagementChain() so it returns the correct
        // result at all element widths
        this.$scope.$watch('$ctrl.maxVisibleManagers', () => {
            this.resetManagerList();
        });
    }

    getManagerCardSize(): string {
        return this.isLargeLayout ? 'small' : 'normal';
    }

    getManagementChain(): IPerson[] {
        // Display managers in a row
        if (this.isLargeLayout) {
            // All managers can fit on screen
            if (this.maxVisibleManagers >= this.managementChain.length) {
                return this.managementChain;
            }

            // Not all managers can fit on screen
            if (!this.visibleManagers) {
                // Show a blank manager as last manager in the chain in place of
                // the last visible manager. Blank manager links to the new last visible manager.
                this.visibleManagers = this.managementChain.slice(0, this.maxVisibleManagers - 1);
                const lastManager = this.managementChain[this.maxVisibleManagers - 2];

                this.visibleManagers.push(<IPerson>({
                    userKey: lastManager.userKey,
                    photoURL: null,
                    displayNames: []
                }));
            }

            return this.visibleManagers;
        }

        // All managers can fit on screen in a column. Order is reversed for ease of rendering and style
        return [].concat(this.managementChain).reverse();
    }

    hasDirectReports(): boolean {
        return this.directReports && !!this.directReports.length;
    }

    hasManagementChain(): boolean {
        return this.managementChain && !!this.managementChain.length;
    }

    onClickPerson(): void {
        if (this.person) {
            this.$state.go('orgchart.search.details', { personId: this.person.userKey });
        }
    }

    selectPerson(userKey: string): void {
        this.$state.go('orgchart.search', { personId: userKey });
    }

    showingOverflow(): boolean {
        return this.visibleManagers &&
            this.managementChain &&
            this.visibleManagers.length < this.managementChain.length;
    }

    private onResize(newValue: number): void {
        this.isLargeLayout = (newValue >= OrgChartSize.Large);
        if (!this.isLargeLayout) {
            this.resetManagerList();
        }
        this.maxVisibleManagers = Math.floor(
         (newValue - 115 /* left margin */) / 125 /* card width + right margin */);
    }

    // Remove all displayed managers so the list is updated on element resize
    private resetManagerList(): void {
        this.visibleManagers = null;
    }
}
