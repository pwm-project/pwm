/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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


import { Component } from '../component';
import { element, IAugmentedJQuery, IFilterService, IScope, IWindowService } from 'angular';
import ElementSizeService from '../ux/element-size.service';
import { IPerson } from '../models/person.model';

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
    stylesheetUrl: require('peoplesearch/orgchart.component.scss'),
    templateUrl: require('peoplesearch/orgchart.component.html')
})
export default class OrgChartComponent {
    directReports: IPerson[];
    elementWidth: number;
    isLargeLayout: boolean;
    managementChain: IPerson[];
    person: IPerson;
    assistant: IPerson;

    private elementSize: OrgChartSize = OrgChartSize.ExtraSmall;
    private maxVisibleManagers: number;
    private visibleManagers: IPerson[];

    static $inject = [ '$element', '$filter', '$scope', '$state', '$window', 'MfElementSizeService' ];
    constructor(
        private $element: IAugmentedJQuery,
        private $filter: IFilterService,
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $window: IWindowService,
        private elementSizeService: ElementSizeService) {
    }

    $onDestroy(): void {
        // TODO: remove $window click listener
    }

    $onInit(): void {
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
