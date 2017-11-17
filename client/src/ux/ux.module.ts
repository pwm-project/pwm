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


import { module } from 'angular';
import AppBarComponent from './app-bar.component';
import AutoCompleteComponent from './auto-complete.component';
import ButtonComponent from './button.component';
import DialogComponent from './dialog.component';
// import { DialogService } from './dialog.service';
import IconButtonComponent from './icon-button.component';
import IconComponent from './icon.component';
import SearchBarComponent from './search-bar.component';
import TableDirectiveFactory from './table.directive';
import TableColumnDirectiveFactory from './table-column.directive';
import ElementSizeService from './element-size.service';
import TabsetDirective from './tabset.directive';

var moduleName = 'peoplesearch.ux';

module(moduleName, [ ])
    .component('mfAppBar', AppBarComponent)
    .component('mfAutoComplete', AutoCompleteComponent)
    .component('mfButton', ButtonComponent)
    .component('mfDialog', DialogComponent)
    .component('mfIconButton', IconButtonComponent)
    .component('mfIcon', IconComponent)
    .component('mfSearchBar', SearchBarComponent)
    .directive('mfTable', TableDirectiveFactory)
    .directive('mfTableColumn', TableColumnDirectiveFactory)
    .directive('mfTabset', TabsetDirective)
    .service('MfElementSizeService', ElementSizeService);
    // .service('MfDialogService', DialogService);

export default moduleName;
