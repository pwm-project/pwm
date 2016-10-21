import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchService from './peoplesearch.service';

var stylesheetUrl = require('peoplesearch/peoplesearch.component.scss');
var templateUrl = require('peoplesearch/peoplesearch.component.html');

@Component({
    stylesheetUrl: stylesheetUrl,
    templateUrl: templateUrl
})
export default class PeopleSearchComponent {
    query: string;
    viewToggleClass: string;

    static $inject = ['$scope', '$state', 'PeopleSearchService'];
    constructor(
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private peopleSearchService: PeopleSearchService) {
    }

    $onInit() {
        this.setViewToggleClass();

        this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            this.peopleSearchService.search(newValue);
        });
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
