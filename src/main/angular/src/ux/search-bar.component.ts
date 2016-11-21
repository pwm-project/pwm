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
import { IAugmentedJQuery, ICompileService, IScope } from 'angular';


@Component({
    bindings: {
        autoFocus: '@',
        onSearchTextChange: '&',
        onBlur: '&',
        onFocus: '&',
        onKeyDown: '&',
        searchText: '<'
    },
    templateUrl: require('ux/search-bar.component.html'),
    stylesheetUrl: require('ux/search-bar.component.scss')
})
export default class SearchBarComponent {
    autoFocus: boolean;
    focused: boolean;
    onSearchTextChange: Function;
    searchText: string;

    static $inject = [ '$compile', '$element', '$scope' ];
    constructor(private $compile: ICompileService,
                private $element: IAugmentedJQuery,
                private $scope: IScope) {
    }

    $onInit(): void {
        this.autoFocus = this.autoFocus !== undefined;

        var self = this;

        this.$scope.$watch('$ctrl.searchText', (newValue: string, oldValue: string) => {
            if (newValue === oldValue) {
                return;
            }

            self.onSearchTextChange({ value: newValue });
        });
    }

    $postLink() {
        const self = this;
        if (this.autoFocus) {
            this.$scope.$evalAsync(() => {
                self.focusInput();
            });
        }
    }

    clearSearchText(): void {
        this.searchText = '';
        this.$element.find('input').val('');
        this.focusInput();
    }

    focusInput() {
        this.$element.find('input')[0].focus();
    }
}
