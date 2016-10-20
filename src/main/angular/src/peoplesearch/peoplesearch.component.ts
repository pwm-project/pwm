import { Component } from '../component';

var templateUrl = require('peoplesearch/peoplesearch.component.html');
var stylesheetUrl = require('peoplesearch/peoplesearch.component.scss');

@Component({
    templateUrl: templateUrl,
    stylesheetUrl: stylesheetUrl
})
export default class PeopleSearchComponent {
    viewToggleClass: string;

    static $inject = ['$state'];
    public constructor(private $state: angular.ui.IStateService) {
    }

    public $onInit() {
        if (this.$state.is('search.table')) {
            this.viewToggleClass = 'fa fa-th-large';
        } else {
            this.viewToggleClass = 'fa fa-list-alt';
        }
    }

    private viewToggleClicked() {
        if (this.$state.is('search.table')) {
            this.$state.go('search.cards');
            this.viewToggleClass = 'fa fa-list-alt';
        } else {
            this.$state.go('search.table');
            this.viewToggleClass = 'fa fa-th-large';
        }
    }
}
