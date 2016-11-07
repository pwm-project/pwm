import { IAttributes, IAugmentedJQuery, IDirective, IScope, ITranscludeFunction } from 'angular';
import Directive from '../directive.ts';
import TableDirectiveController from './table.directive.controller';

interface ITableDirectiveScope extends IScope {
    autoSize: boolean;
}

@Directive({
    controller: TableDirectiveController,
    restrict: 'E',
    scope: {
        autoSize: '@'
    },
    stylesheetUrl: require('ux/table.directive.scss'),
    templateUrl: require('ux/table.directive.html'),
    transclude: true
})
export default class TableDirective implements IDirective {
    static $inject = [];
    constructor() {
    }

    static link($scope: ITableDirectiveScope,
         element: IAugmentedJQuery,
         attributes: IAttributes,
         transclude: ITranscludeFunction) {
    }

    static factory(): IDirective {
        return TableDirective;
    }
}
