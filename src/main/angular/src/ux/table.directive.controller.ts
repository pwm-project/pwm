import { IScope } from 'angular';

class Column {
    constructor(public label: string,
                public valueExpression: string,
                public visible?: boolean) {
        this.visible = visible !== false;
    }
}

export default class TableDirectiveController {
    columns: Column[];
    items: any[];
    itemName: string;
    showConfiguration: boolean = false;
    sortColumn: Column;
    sortReverse: boolean = false;

    static $inject = [ '$scope' ];
    constructor(private $scope: IScope) {
        this.columns = [];
    }

    addColumn(label: string, valueExpression: string): void {
        this.columns.push(new Column(this.$scope.$eval(label), valueExpression));
    }

    getItems(): any[] {
        if (this.sortColumn) {
            var self = this;

            return this.items.sort((item1: any, item2: any): number => {
                var value1 = this.getValue(item1, self.sortColumn.valueExpression);
                var value2 = this.getValue(item2, self.sortColumn.valueExpression);

                var result = 0;

                if (value1 < value2) {
                    result = -1;
                }
                else if (value1 > value2) {
                    result = 1;
                }

                return self.sortReverse ? -result : result;
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

    sortOnColumn(column: Column): void {
        if (this.sortColumn === column) {
            // Reverse sort order if the column has already been sorted
            this.sortReverse = !this.sortReverse;
        }
        else {
            // Reset sort order to normal sort order
            this.sortReverse = false;
        }

        this.sortColumn = column;
    }

    static toggleColumnVisibility(column: Column): void {
        column.visible = !column.visible;
    }

    reverseSort(): void {
        this.sortOnColumn(this.sortColumn);
    }
}
