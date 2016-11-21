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


import { Component } from '../component';
import { IAugmentedJQuery, ICompileService, IDocumentService, IPromise, IScope } from 'angular';

@Component({
    bindings: {
        'search': '&',
        'itemSelected': '&',
        'item': '@',
        'itemText': '@',
        'query': '='
    },
    templateUrl: require('ux/auto-complete.component.html'),
    transclude: true,
    stylesheetUrl: require('ux/auto-complete.component.scss')
})
export default class AutoCompleteComponent {
    item: string;
    items: any[];
    itemSelected: (item: any) => void;
    query: string;
    search: (query: any) => IPromise<any[]>;
    searchMessage: string;
    selectedIndex: number;
    show: boolean;

    static $inject = [ '$compile', '$document', '$element', '$scope' ];
    constructor(private $compile: ICompileService,
                private $document: IDocumentService,
                private $element: IAugmentedJQuery,
                private $scope: IScope) {
    }

    $onInit(): void {
        const self = this;

        this.$scope.$watch('$ctrl.query', () => {
            self.search({ query: self.query })
                .then((results: any[]) => {
                    self.items = results;
                    self.resetSelection();
                    self.showAutoCompleteResults();
                });
        });

        this.selectedIndex = -1;

        // Listen for clicks outside of the auto-complete component
        this.$document.on('click', (event: Event) => {
            if (self.show) {
                self.hideAutoCompleteResults();
                self.$scope.$apply();
            }
        });
    }

    $postLink(): void {
        // Remove content template from dom
        const contentTemplate: IAugmentedJQuery = this.$element.find('content-template');
        // noinspection TypeScriptUnresolvedFunction
        contentTemplate.remove();

        const autoCompleteHtml =
            '<ul class="results" ng-if="$ctrl.show" ng-click="$event.stopPropagation()">' +
            '   <li ng-repeat="item in $ctrl.items"' +
            '       ng-click="$ctrl.selectItem(item)"' +
            '       ng-class="{ \'selected\': $index == $ctrl.selectedIndex }\">' +
            contentTemplate.html().replace(new RegExp(this.item, 'g'), 'item') +
            '   </li>' +
            '   <li class="search-message" ng-if="$ctrl.show && $ctrl.query && !$ctrl.items.length">' +
            '       <span translate="Display_SearchResultsNone"></span>' +
            '   </li>' +
            '</ul>';
        const compiledElement = this.$compile(autoCompleteHtml)(this.$scope);

        this.$element.append(compiledElement);
    }

    onSearchBarFocus(): void {
        if (this.hasItems()) {
            this.showAutoCompleteResults();
        }
    }

    onSearchBarKeyDown(event: KeyboardEvent): void {
        switch (event.keyCode) {
            case 40: // ArrowDown
                this.selectNextItem();
                event.preventDefault();
                break;
            case 38: // ArrowUp
                this.selectPreviousItem();
                event.preventDefault();
                break;
            case 27: // Escape
                if (!this.show || !this.hasItems()) {
                    this.clearAutoCompleteResults();
                }
                else {
                    this.hideAutoCompleteResults();
                }

                break;
            case 13: // Enter
                if (this.hasItems() && this.show) {
                    const item = this.getSelectedItem();
                    this.selectItem(item);
                }
                break;
            case 9: // Tab
                if (!this.query) {
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
        this.hideAutoCompleteResults();

        const data = {};
        data[this.item] = item;
        this.itemSelected(data);
    }

    private clearAutoCompleteResults(): void {
        this.resetSelection();
        this.query = null;
        this.items = [];
    }

    private getSelectedItem(): any {
        return this.items[this.selectedIndex];
    }

    private hasItems(): boolean {
        return this.items && !!this.items.length;
    }

    private hideAutoCompleteResults(): void  {
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

    private showAutoCompleteResults(): void  {
        if (this.hasItems() || !/^\s*$/.test(this.query)) {
            this.show = true;
        }
    }
}
