/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import { IAugmentedJQuery } from 'angular';
import ElementSizeService from './element-size.service';

export enum AppBarSize {
    Large = 413,
    ExtraLarge = 473
}

@Component({
    stylesheetUrl: require('ux/app-bar.component.scss'),
    template: `<div class="mf-app-bar-content" ng-transclude></div>`,
    transclude: true
})
export default class AppBarComponent {
    static $inject = [ '$element', 'MfElementSizeService' ];
    constructor(private $element: IAugmentedJQuery, private elementSizeService: ElementSizeService) {
    }

    $onInit(): void {
        this.elementSizeService.watchWidth(this.$element, AppBarSize);
    }
}
