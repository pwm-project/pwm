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


import { IAttributes, IAugmentedJQuery, IDirective, IDocumentService, IParseService, IScope } from 'angular';
import TableDirectiveController from './table.directive.controller';

require('ux/table.directive.scss');
var templateUrl = require('ux/table.directive.html');

class DataExpression {
    constructor(public itemName: string,
                public collectionExpression: string) {}
}

class TableDirective implements IDirective {
    controller = TableDirectiveController;
    controllerAs = 'table';
    restrict = 'E';
    templateUrl = templateUrl;
    transclude = true;

    constructor(private $document: IDocumentService, private $parse: IParseService) {}

    link($scope: IScope,
         instanceElement: IAugmentedJQuery,
         instanceAttributes: IAttributes,
         controller: TableDirectiveController): void {
        if (instanceAttributes['onClickItem']) {
            controller.onClickItem = this.$parse(instanceAttributes['onClickItem']);
        }

        var dataExpression: DataExpression = this.parseDataExpression(instanceAttributes['data']);

        controller.itemName = dataExpression.itemName;
        // Collection may not be immediately available (i.e. promise). Watch its value for changes
        $scope.$watch(dataExpression.collectionExpression, (items: any[]) => {
            controller.items = items;
        });

        // Listen for clicks outside of the configuration panel
        this.$document.on('click', (event: Event) => {
            if (controller.showConfiguration) {
                controller.hideConfiguration();
                $scope.$apply();
            }
        });

        // Clean up event listeners
        $scope.$on('$destroy', () => {
            instanceElement.off();
        });
    }

    parseDataExpression(dataExpression: string): any {
        // Parse data expression from [data] attribute
        var match: RegExpMatchArray = dataExpression.match(/^\s*(.+)\s+in\s+(.*?)\s*$/);
        if (!match) {
            throw Error('Expected expression in [data] attribute in form of "[ITEM] in [COLLECTION]"');
        }

        return new DataExpression(match[1], match[2]);
    }
}

TableDirectiveFactory.$inject = [ '$document', '$parse' ];
export default function TableDirectiveFactory($document: IDocumentService, $parse: IParseService): IDirective {
    return new TableDirective($document, $parse);
};
