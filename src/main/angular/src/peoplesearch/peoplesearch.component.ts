import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchService from './peoplesearch.service';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch.component.html')
})
export default class PeopleSearchComponent {
    query: string;
    viewToggleClass: string;

    static $inject = ['$scope', '$state', '$stateParams', 'PeopleSearchService'];
    constructor(
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $stateParams: angular.ui.IStateParamsService,
        private peopleSearchService: PeopleSearchService) {
    }

    $onInit() {
        this.setViewToggleClass();

        this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            this.peopleSearchService.search(newValue);
        });

        this.query = this.$stateParams['query'];
    }

    private setViewToggleClass() {
        this.viewToggleClass = this.$state.is('search.table') ? 'fa fa-th-large' : 'fa fa-list-alt';
    }

    private viewToggleClicked() {
        this.setViewToggleClass();

        if (this.$state.is('search.table')) {
            this.$state.go('search.cards');
        }
        else {
            this.$state.go('search.table');
        }
    }
}
