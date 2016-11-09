import { IAttributes, IAugmentedJQuery, IDirective, IScope } from 'angular';
import Directive from '../directive.ts';
import TableDirectiveController from './table.directive.controller';

interface ITableColumnDirectiveScope extends IScope {
    label: string;
    order: number;
    sortable: boolean;
    value: string;
    visible: boolean;
}

@Directive({
    require: '^mfTable',
    restrict: 'E'
})
export default class TableColumnDirective implements IDirective {
    static $inject = [];
    constructor() {
    }

    static link($scope: ITableColumnDirectiveScope,
                instanceElement: IAugmentedJQuery,
                instanceAttributes: IAttributes,
                tableController: TableDirectiveController): void {
        tableController.addColumn(instanceAttributes['label'], instanceAttributes['value']);
    }

    static factory(): IDirective {
        return TableColumnDirective;
    }
}
