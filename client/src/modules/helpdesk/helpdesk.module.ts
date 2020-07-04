/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// These need to be at the top so imported components can override the default styling
require('../../styles.scss');
require('../peoplesearch/peoplesearch.scss');

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
import CommonSearchService from '../../services/common-search.service';

const moduleName = 'help-desk';

module(moduleName, [
    'ngAria',
    'ngSanitize',
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
    .service('LocalStorageService', LocalStorageService)
    .service('CommonSearchService', CommonSearchService);

export default moduleName;
