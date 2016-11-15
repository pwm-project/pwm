import { Component } from '../component';
import { IConfigService } from '../services/config.service';
import IPeopleService from '../services/people.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import { IScope } from 'angular';

@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-table.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-table.component.html')
})
export default class PeopleSearchTableComponent extends PeopleSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [ '$scope', '$state', '$stateParams', 'ConfigService', 'PeopleService' ];
    constructor($scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                private configService: IConfigService,
                peopleService: IPeopleService) {
        super($scope, $state, $stateParams, peopleService);
    }

    $onInit(): void {
        this.initialize(this.peopleService.cardSearch);

        let self = this;

        // The table columns are dynamic and configured via a service
        this.configService.getColumnConfiguration().then((columnConfiguration: any) => {
            self.columnConfiguration = columnConfiguration;
        });
    }

    gotoCardsView() {
        this.gotoState('search.cards');
    }
}
