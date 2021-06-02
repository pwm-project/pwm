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
require('./peoplesearch.scss');

import 'angular-aria';

import {IComponentOptions, module} from 'angular';
import { HighlightFilter } from './string.filters';
import { FullNameFilter } from './person.filters';
import OrgChartComponent from './orgchart.component';
import OrgChartSearchComponent from './orgchart-search.component';
import PeopleSearchTableComponent from './peoplesearch-table.component';
import PeopleSearchCardsComponent from './peoplesearch-cards.component';
import PersonCardDirective from './person-card.component';
import PersonDetailsDialogComponent from './person-details-dialog.component';
import LocalStorageService from '../../services/local-storage.service';
import PromiseService from '../../services/promise.service';
import uxModule from '../../ux/ux.module';
import CommonSearchService from '../../services/common-search.service';
import OrgchartExportController from './orgchart-export.controller';
import OrgchartEmailController from './orgchart-email.controller';

const moduleName = 'people-search';

module(moduleName, [
    'ngAria',
    'pascalprecht.translate',
    uxModule
])
    .filter('fullName', FullNameFilter)
    .filter('highlight', HighlightFilter)
    .component('orgChart', OrgChartComponent as IComponentOptions)
    .component('orgChartSearch', OrgChartSearchComponent as IComponentOptions)
    .directive('personCard', PersonCardDirective)
    .component('peopleSearchTable', PeopleSearchTableComponent as IComponentOptions)
    .component('peopleSearchCards', PeopleSearchCardsComponent as IComponentOptions)
    .component('personDetailsDialogComponent', PersonDetailsDialogComponent as IComponentOptions)
    .controller('OrgchartExportController', OrgchartExportController)
    .controller('OrgchartEmailController', OrgchartEmailController)
    .service('PromiseService', PromiseService)
    .service('LocalStorageService', LocalStorageService)
    .service('CommonSearchService', CommonSearchService);

export default moduleName;
