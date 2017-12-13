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
import HelpDeskSearchComponent from './helpdesk-search.component';
import uxModule from '../ux/ux.module';
import PersonCardComponent from '../peoplesearch/person-card.component';
import PromiseService from '../services/promise.service';
import HelpDeskDetailComponent from './helpdesk-detail.component';
import LocalStorageService from '../services/local-storage.service';
import VerificationsDialogController from './verifications-dialog.controller';
import ObjectService from '../services/object.service';
import RecentVerificationsDialogController from './recent-verifications-dialog.controller';
import {DateFilter} from './date.filters';

require('../peoplesearch/peoplesearch.scss');

const moduleName = 'help-desk';

module(moduleName, [
    uxModule
])

    .component('helpDeskSearch', HelpDeskSearchComponent)
    .component('helpDeskDetail', HelpDeskDetailComponent)
    .component('personCard', PersonCardComponent)
    .controller('VerificationsDialogController', VerificationsDialogController)
    .controller('RecentVerificationsDialogController', RecentVerificationsDialogController)
    .filter('dateFilter', DateFilter)
    .service('ObjectService', ObjectService)
    .service('PromiseService', PromiseService)
    .service('LocalStorageService', LocalStorageService);

export default moduleName;
