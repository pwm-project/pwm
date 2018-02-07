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


import { IScope } from 'angular';
import Column from './../models/column.model';
import { IFilterService } from 'angular';
import * as angular from 'angular';

export default class TableDirectiveController {
    columns: Column[];
    items: any[];
    itemName: string;
    onClickItem: (scope: IScope, locals: any) => void;
    searchHighlight: string;
    showConfiguration: boolean = false;
    sortColumn: Column;
    reverseSort: boolean = false;

    static $inject = [ '$filter', '$scope' ];
    constructor(private $filter: IFilterService, private $scope: IScope) {
        this.columns = [];
    }

    addColumn(label: string, valueExpression: string): void {
        this.columns.push(new Column(label, valueExpression));
    }

    clickItem(item: any, event: Event) {
        const locals = {};
        locals[this.itemName] = item;

        this.onClickItem(this.$scope, locals);

        event.stopImmediatePropagation();
    }

    getItems(): any[] {
        if (this.items && this.sortColumn) {
            const self = this;

            return this.items.concat().sort((item1: any, item2: any): number => {
                const value1 = self.getValue(item1, self.sortColumn.valueExpression);
                const value2 = self.getValue(item2, self.sortColumn.valueExpression);

                let result = 0;

                // value 1 is undefined but not value 2
                if (angular.isUndefined(value1) && !angular.isUndefined(value2)) {
                    result = -1;
                }
                // value 2 is undefined but not value 1
                else if (angular.isUndefined(value2) && !angular.isUndefined(value1)) {
                    result = 1;
                }
                // Both values are numbers
                else if (angular.isNumber(value1) && angular.isNumber(value2)) {
                    result = value1 - value2;
                }
                // Both values are strings
                else if (angular.isString(value1) && angular.isString(value2)) {
                    result = value1.localeCompare(value2);
                }

                return self.reverseSort ? -result : result;
            });
        }

        return this.items;
    }

    // getDisplayValue(item: any, valueExpression: string): any {
    //     let value = this.getValue(item, valueExpression);
    //
    //     if (this.searchHighlight) {
    //         return this
    //             .$filter<(input: string, searchText: string) => string>('highlight')(value, this.searchHighlight);
    //     }
    //
    //     return value;
    // }

    getValue(item: any, valueExpression: string): any {
        const locals: any = {};
        // itemName comes from directive's link function
        locals[this.itemName] = item;

        return this.$scope.$eval(valueExpression, locals);
    }

    getVisibleColumns(): Column[] {
        return this.columns.filter((column: Column) => column.visible);
    }

    hideConfiguration(): void {
        this.showConfiguration = false;
    }

    sortOnColumn(column: Column): void {
        if (this.sortColumn === column) {
            this.toggleSortOrder();
        }
        else {
            // Reset sort order to normal sort order
            this.reverseSort = false;
        }

        this.sortColumn = column;
    }

    toggleConfigurationVisibility(event: Event) {
        this.showConfiguration = !this.showConfiguration;

        event.stopImmediatePropagation();
    }

    toggleSortOrder(): void {
        this.reverseSort = !this.reverseSort;
    }
}
