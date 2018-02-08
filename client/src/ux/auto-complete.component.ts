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


import { Component } from '../component';
import { IAttributes, IAugmentedJQuery, IDocumentService, IPromise, IScope } from 'angular';

@Component({
    bindings: {
        'onSearchTextChange': '&',
        'inputDebounce': '<',
        'itemSelected': '&',
        'item': '@',
        'itemText': '@',
        'searchFunction': '&search',
        'searchText': '<'
    },
    template: [
        '$element',
        '$attrs',
        ($element: IAugmentedJQuery, $attrs: IAttributes): string => {
            // Remove content template from dom
            const contentTemplate: IAugmentedJQuery = $element.find('content-template');
            contentTemplate.detach();

            return `
                <mf-search-bar input-debounce="$ctrl.inputDebounce"
                               search-text="$ctrl.searchText"
                               on-search-text-change="$ctrl.onSearchBarTextChange(value)"
                               on-key-down="$ctrl.onSearchBarKeyDown($event)"
                               ng-click="$ctrl.onSearchBarClick($event)"
                               auto-focus></mf-search-bar>
                <ul class="results" ng-if="$ctrl.show" ng-click="$event.stopPropagation()">
                    <li ng-repeat="item in $ctrl.items"
                       ng-click="$ctrl.selectItem(item)"
                       ng-class="{ \'selected\': $index == $ctrl.selectedIndex }\">` +
                contentTemplate.html().replace(new RegExp($attrs['item'], 'g'), 'item') +
                    `</li>
                    <li class="search-message"
                        ng-if="$ctrl.show && $ctrl.searchText && !$ctrl.loading && !$ctrl.items.length">
                        <span translate="Display_SearchResultsNone"></span>
                    </li>
                </ul>`;
        }],
    stylesheetUrl: require('ux/auto-complete.component.scss')
})
export default class AutoCompleteComponent {
    item: string;
    items: any[];
    itemSelected: (item: any) => void;
    loading: boolean;
    onSearchTextChange: Function;
    searchText: string;
    searchFunction: (query: any) => IPromise<any[]>;
    searchMessage: string;
    selectedIndex: number;
    show: boolean;

    static $inject = [ '$document', '$element', '$scope' ];
    constructor(private $document: IDocumentService,
                private $element: IAugmentedJQuery,
                private $scope: IScope) {
    }

    $onDestroy(): void {
        this.$document.off('click', this.onDocumentClick.bind(this));
    }

    $onInit(): void {
        this.selectedIndex = -1;

        if (this.searchText) {
            this.fetchAutoCompleteData(this.searchText);
        }

        this.hideResults();
    }

    $postLink(): void {
        let self = this;

        // Listen for clicks outside of the auto-complete component
        // Implemented as a click event instead of a blur event, so the results list can be clicked
        this.$document.on('click', this.onDocumentClick.bind(this));
    }

    onSearchBarClick(event: Event): void {
        event.stopImmediatePropagation();
    }

    onSearchBarFocus(): void {
        if (this.hasItems()) {
            this.showResults();
        }
    }

    onSearchBarTextChange(value: string): void {
        this.searchText = value;
        this.fetchAutoCompleteData(value);
        this.showResults();

        this.onSearchTextChange({ value: value });
    }

    onSearchBarKeyDown(event: KeyboardEvent): void {
        switch (event.keyCode) {
            case 40: // ArrowDown
                if (this.hasItems() && !this.show) {
                    this.showResults();
                }
                else {
                    this.selectNextItem();
                    event.preventDefault();
                }
                break;
            case 38: // ArrowUp
                this.selectPreviousItem();
                event.preventDefault();
                break;
            case 27: // Escape
                if (!this.show || !this.hasItems()) {
                    this.clearResults();
                }
                else {
                    this.hideResults();
                }

                break;
            case 13: // Enter
                if (this.hasItems() && this.show) {
                    const item = this.getSelectedItem();
                    this.selectItem(item);
                }
                break;
            case 9: // Tab
                if (!this.searchText || !this.show) {
                    return;
                }

                if (event.shiftKey) {
                    this.selectPreviousItem();
                }
                else {
                    this.selectNextItem();
                }

                event.preventDefault();
                break;
        }
    }

    selectItem(item: any): void {
        this.clearResults();

        const data = {};
        data[this.item] = item;
        this.itemSelected(data);
    }

    private clearResults(): void {
        this.resetSelection();
        this.searchText = null;
        this.items = [];
    }

    private onDocumentClick(): void {
        if (this.show) {
            this.hideResults();
            this.$scope.$apply();
        }
    }

    private fetchAutoCompleteData(value: string): void {
        this.loading = true;
        const self = this;
        this.searchFunction({ query: value })
            .then((results: any[]) => {
                self.items = results;
                self.resetSelection();
            })
            .finally(() => {
                self.loading = false;
            });
    }

    private getSelectedItem(): any {
        return this.items[this.selectedIndex];
    }

    private hasItems(): boolean {
        return this.items && !!this.items.length;
    }

    private hideResults(): void  {
        this.show = false;
    }

    private resetSelection(): void {
        this.selectedIndex = 0;
    }

    private selectNextItem(): void {
        if (this.hasItems() && this.selectedIndex < this.items.length - 1) {
            this.selectedIndex++;
        }
    }

    private selectPreviousItem(): void {
        if (this.hasItems() && this.selectedIndex > 0) {
            this.selectedIndex--;
        }
    }

    private showResults(): void  {
        this.show = true;
    }
}
