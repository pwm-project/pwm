import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchService from './peoplesearch.service';
import Person from '../models/person.model';

declare var PWM_PS: any;

@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-table.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-table.component.html')
})
export default class PeopleSearchTableComponent {
    private deregistrationCallback: () => void;
    people: Person[];

    static $inject = [ '$scope', '$state', 'PeopleSearchService' ];
    constructor(
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private peopleSearchService: PeopleSearchService) {
    }

    $onInit() {
        this.getPeople();

        var self = this;
        this.deregistrationCallback = this.$scope.$on('people-updated', () => {
            self.getPeople();
        });
    }

    $onDestroy() {
        this.deregistrationCallback();
    }

    getPeople() {
        this.people = this.peopleSearchService.people;
    }

    selectPerson(id: string) {
        // this.$state.go('orgchart', { personId: id });

        // TODO: This is here to temporarily pull up the old modal dialog:
        PWM_PS.showUserDetail(id);
    }
}
