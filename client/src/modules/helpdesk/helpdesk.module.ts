/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import 'angular-aria';

import {IComponentOptions, module} from 'angular';
import DateFilter from './date.filters';
import HelpDeskDetailComponent from './helpdesk-detail.component';
import HelpDeskSearchTableComponent from './helpdesk-search-table.component';
import HelpDeskSearchCardsComponent from './helpdesk-search-cards.component';
import LocalStorageService from '../../services/local-storage.service';
import ObjectService from '../../services/object.service';
import PersonCardDirective from '../peoplesearch/person-card.component';
import PromiseService from '../../services/promise.service';
import RecentVerificationsDialogController from './recent-verifications-dialog.controller';
import uxModule from '../../ux/ux.module';
import VerificationsDialogController from './verifications-dialog.controller';
import AutogenChangePasswordController from '../../components/changepassword/autogen-change-password.controller';
import RandomChangePasswordController from '../../components/changepassword/random-change-password.controller';
import SuccessChangePasswordController from '../../components/changepassword/success-change-password.controller';
import TypeChangePasswordController from '../../components/changepassword/type-change-password.controller';

require('../peoplesearch/peoplesearch.scss');

const moduleName = 'help-desk';

module(moduleName, [
    'ngAria',
    uxModule
])

    .component('helpDeskSearchCards', HelpDeskSearchCardsComponent as IComponentOptions)
    .component('helpDeskSearchTable', HelpDeskSearchTableComponent as IComponentOptions)
    .component('helpDeskDetail', HelpDeskDetailComponent as IComponentOptions)
    .directive('personCard', PersonCardDirective)
    .controller('AutogenChangePasswordController', AutogenChangePasswordController)
    .controller('RandomChangePasswordController', RandomChangePasswordController)
    .controller('RecentVerificationsDialogController', RecentVerificationsDialogController)
    .controller('SuccessChangePasswordController', SuccessChangePasswordController)
    .controller('TypeChangePasswordController', TypeChangePasswordController)
    .controller('VerificationsDialogController', VerificationsDialogController)
    .filter('dateFilter', DateFilter)
    .service('ObjectService', ObjectService)
    .service('PromiseService', PromiseService)
    .service('LocalStorageService', LocalStorageService);

export default moduleName;
