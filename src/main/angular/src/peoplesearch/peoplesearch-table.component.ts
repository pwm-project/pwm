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
import { IConfigService } from '../services/config.service';
import IPeopleService from '../services/people.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import { IQService, IScope } from 'angular';
import SearchResult from '../models/search-result.model';

@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-table.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-table.component.html')
})
export default class PeopleSearchTableComponent extends PeopleSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [ '$q', '$scope', '$state', '$stateParams', '$translate', 'ConfigService', 'PeopleService' ];
    constructor($q: IQService,
                $scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                $translate: angular.translate.ITranslateService,
                private configService: IConfigService,
                peopleService: IPeopleService) {
        super($q, $scope, $state, $stateParams, $translate, peopleService);
    }

    $onInit(): void {
        this.initialize();
        this.fetchData();

        let self = this;

        // The table columns are dynamic and configured via a service
        this.configService.getColumnConfiguration().then((columnConfiguration: any) => {
            self.columnConfiguration = columnConfiguration;
        });
    }

    gotoCardsView() {
        this.gotoState('search.cards');
    }

    fetchData() {
        let searchResult = this.fetchSearchData();
        if (searchResult) {
            searchResult.then(this.onSearchResult.bind(this));
        }
    }

    private onSearchResult(searchResult: SearchResult): void {
        this.searchResult = searchResult;
    }
}
