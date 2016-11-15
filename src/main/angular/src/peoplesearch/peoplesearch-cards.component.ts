import { Component } from '../component';
import IPeopleService from '../services/people.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import { IScope } from 'angular';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-cards.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-cards.component.html')
})
export default class PeopleSearchCardsComponent extends PeopleSearchBaseComponent {
    static $inject = [ '$scope', '$state', '$stateParams', 'PeopleService' ];
    constructor($scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                peopleService: IPeopleService) {
        super($scope, $state, $stateParams, peopleService);
    }

    $onInit(): void {
        super.$onInit();
        this.fetchData(this.peopleService.cardSearch);
    }

    gotoTableView() {
        super.gotoState('search.table');
    }
}
