import { IAttributes, IAugmentedJQuery, IDirective, IScope } from 'angular';
import TableDirectiveController from './table.directive.controller';

class TableColumnDirective implements IDirective {
    require: string = '^mfTable';
    restrict: string = 'E';

    constructor() {}

    link($scope: IScope,
         instanceElement: IAugmentedJQuery,
         instanceAttributes: IAttributes,
         tableController: TableDirectiveController): void {
        tableController.addColumn(instanceAttributes['label'], instanceAttributes['value']);
    }
}

TableColumnDirectiveFactory.$inject = [];
export default function TableColumnDirectiveFactory(): IDirective {
    return new TableColumnDirective();
};
