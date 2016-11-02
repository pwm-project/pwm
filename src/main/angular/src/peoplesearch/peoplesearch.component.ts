import { Component } from '../component';
import { IScope, ILocationService } from 'angular';
import PeopleSearchService from './peoplesearch.service';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch.component.html')
})
export default class PeopleSearchComponent {
    query: string;
    viewToggleClass: string;

    tableIconClass: string = 'fa fa-list-alt';
    cardIconClass: string = 'fa fa-th-large';

    static $inject = ['$scope', '$state', '$stateParams', '$location', 'PeopleSearchService'];
    constructor(
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $stateParams: angular.ui.IStateParamsService,
        private $location: ILocationService,
        private peopleSearchService: PeopleSearchService) {
    }

    $onInit() {
        this.setViewToggleClass();

        this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            this.peopleSearchService.search(newValue);
            this.$location.search('query', newValue);
        });

        this.query = this.$stateParams['query'];
    }

    private setViewToggleClass() {
        this.viewToggleClass = this.$state.is('search.table') ? this.cardIconClass : this.tableIconClass;
    }

    private viewToggleClicked() {
        let nextState: string = this.$state.is('search.table') ? 'search.cards' : 'search.table';

        this.$state.go(nextState).then(() => {
            this.setViewToggleClass();
        });
    }
}
