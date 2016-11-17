import { IScope } from 'angular';
import Column from './../models/column.model';
import * as angular from 'angular';

export default class TableDirectiveController {
    columns: Column[];
    items: any[];
    itemName: string;
    onClickItem: (scope: IScope, locals: any) => void;
    showConfiguration: boolean = false;
    sortColumn: Column;
    reverseSort: boolean = false;

    static $inject = [ '$scope' ];
    constructor(private $scope: IScope) {
        this.columns = [];
    }

    addColumn(label: string, valueExpression: string): void {
        this.columns.push(new Column(label, valueExpression));
    }

    clickItem(item: any, event: Event) {
        var locals = {};
        locals[this.itemName] = item;

        this.onClickItem(this.$scope, locals);

        event.stopImmediatePropagation();
    }

    getItems(): any[] {
        if (this.items && this.sortColumn) {
            var self = this;

            return this.items.concat().sort((item1: any, item2: any): number => {
                var value1 = self.getValue(item1, self.sortColumn.valueExpression);
                var value2 = self.getValue(item2, self.sortColumn.valueExpression);

                var result = 0;

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

    getValue(item: any, valueExpression: string) {
        var locals: any = {};
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
