import {
    IAttributes,
    IAugmentedJQuery,
    ICompileService,
    IDirective,
    IScope,
    ITranscludeFunction } from 'angular';
import Directive from '../directive.ts';
import TableDirectiveController from './table.directive.controller';

interface ITableDirectiveScope extends IScope {
    items: any[];
}

@Directive({
    controller: TableDirectiveController,
    controllerAs: 'table',
    restrict: 'E',
    stylesheetUrl: require('ux/table.directive.scss'),
    templateUrl: require('ux/table.directive.html'),
    transclude: true
})
export default class TableDirective implements IDirective {
    static $inject = [ '$compile' ];
    constructor(private $compile: ICompileService) {
    }

    static link($scope: ITableDirectiveScope,
                instanceElement: IAugmentedJQuery,
                instanceAttributes: IAttributes,
                controller: TableDirectiveController): void {
        var dataExpression: string = instanceAttributes['data'];

        // TODO: Implement 'track by' functionality
        // TODO: Support objects, not just arrays (ex: data="(key, value) in [OBJECT]")
        // Parse data expression from [data] attribute
        var match: RegExpMatchArray = dataExpression.match(/^\s*(.+)\s+in\s+(.*?)\s*$/);
        if (!match) {
            throw Error('Expected expression in [data] in form of "[ITEM] in [COLLECTION]"');
        }

        // Set itemName on controller for $eval execution
        controller.itemName = match[1];
        // Collection may not be immediately available (i.e. promise). Watch its value for changes
        $scope.$watch(match[2], (items: any[]) => { controller.items = items; });
    }

    static factory(): IDirective {
        return TableDirective;
    }
}
