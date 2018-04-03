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


import {IQService, IScope, ITimeoutService} from 'angular';
import HelpDeskSearchBaseComponent from './helpdesk-search-base.component';
import {Component} from '../../component';
import SearchResult from '../../models/search-result.model';
import {IHelpDeskConfigService} from '../../services/helpdesk-config.service';
import LocalStorageService from '../../services/local-storage.service';
import PromiseService from '../../services/promise.service';
import {IHelpDeskService} from '../../services/helpdesk.service';
import IPwmService from '../../services/pwm.service';

@Component({
    stylesheetUrl: require('modules/helpdesk/helpdesk-search.component.scss'),
    templateUrl: require('modules/helpdesk/helpdesk-search-table.component.html')
})
export default class HelpDeskSearchTableComponent extends HelpDeskSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [
        '$q',
        '$scope',
        '$state',
        '$stateParams',
        '$timeout',
        '$translate',
        'ConfigService',
        'HelpDeskService',
        'IasDialogService',
        'LocalStorageService',
        'PromiseService',
        'PwmService'
    ];
    constructor($q: IQService,
                $scope: IScope,
                private $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                $timeout: ITimeoutService,
                $translate: angular.translate.ITranslateService,
                configService: IHelpDeskConfigService,
                helpDeskService: IHelpDeskService,
                IasDialogService: any,
                localStorageService: LocalStorageService,
                promiseService: PromiseService,
                pwmService: IPwmService) {
        super($q, $scope, $stateParams, $timeout, $translate, configService, helpDeskService, IasDialogService,
              localStorageService, promiseService, pwmService);
    }

    $onInit() {
        this.initialize();
        this.fetchData();

        // The table columns are dynamic and configured via a service
        this.configService.getColumnConfig().then(
            (columnConfiguration: any) => {
                this.columnConfiguration = Object.keys(columnConfiguration).reduce(
                    function(accumulator, columnId) {
                        accumulator[columnId] = {
                            label: columnConfiguration[columnId],
                            visible: true
                        };

                        return accumulator;
                    },
                    {});
            },
            (error) => {
                this.setErrorMessage(error);
            });
    }

    fetchData() {
        let searchResult = this.fetchSearchData();
        if (searchResult) {
            searchResult.then(this.onSearchResult.bind(this));
        }
    }

    gotoCardsView(): void {
        this.$state.go('search.cards', {query: this.query});
    }

    toggleColumnVisible(event, columnId): void {
        const visibleColumns = Object.keys(this.columnConfiguration).filter((columnId) => {
            return this.columnConfiguration[columnId].visible;
        });

        if (!(visibleColumns.length === 1 && this.columnConfiguration[columnId].visible)) {
            this.columnConfiguration[columnId].visible = !this.columnConfiguration[columnId].visible;
        }

        event.stopImmediatePropagation();
    }

    private onSearchResult(searchResult: SearchResult): void {
        this.searchResult = searchResult;
    }
}
