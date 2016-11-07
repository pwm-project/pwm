import { IAttributes, IAugmentedJQuery, IDirective, IScope, ITranscludeFunction } from 'angular';
import Directive from '../directive.ts';
import TableColumnDirectiveController from './table-column.directive.controller';

interface ITableColumnDirectiveScope extends IScope {
    label: string;
    order: number;
    sortable: boolean;
    value: string;
    visible: boolean;
}

@Directive({
    bindToController: true,
    controller: TableColumnDirectiveController,
    restrict: 'E',
    scope: {
        label: '@'
    },
    templateUrl: require('ux/table-column.directive.html'),
    transclude: false
})
export default class TableColumnDirective implements IDirective {
    static $inject = [];
    constructor() {
    }

    static link($scope: ITableColumnDirectiveScope,
         element: IAugmentedJQuery,
         attributes: IAttributes,
         transclude: ITranscludeFunction) {
    }

    static factory(): IDirective {
        return TableColumnDirective;
    }
}
