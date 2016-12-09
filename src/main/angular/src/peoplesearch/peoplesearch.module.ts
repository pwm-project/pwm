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


import { module } from 'angular';
import { HighlightFilter } from './string.filters';
import { FullNameFilter } from './person.filters';
import OrgChartComponent from './orgchart.component';
import OrgChartSearchComponent from './orgchart-search.component';
import PeopleSearchTableComponent from './peoplesearch-table.component';
import PeopleSearchCardsComponent from './peoplesearch-cards.component';
import PersonCardComponent from './person-card.component';
import PersonDetailsDialogComponent from './person-details-dialog.component';
import LocalStorageService from '../services/local-storage.service';
import PromiseService from '../services/promise.service';
import uxModule from '../ux/ux.module';

require('./peoplesearch.scss');

const moduleName = 'people-search';

module(moduleName, [
    'pascalprecht.translate',
    uxModule
])
    .filter('fullName', FullNameFilter)
    .filter('highlight', HighlightFilter)
    .component('orgChart', OrgChartComponent)
    .component('orgChartSearch', OrgChartSearchComponent)
    .component('personCard', PersonCardComponent)
    .component('peopleSearchTable', PeopleSearchTableComponent)
    .component('peopleSearchCards', PeopleSearchCardsComponent)
    .component('personDetailsDialogComponent', PersonDetailsDialogComponent)
    .service('PromiseService', PromiseService)
    .service('LocalStorageService', LocalStorageService);

export default moduleName;
