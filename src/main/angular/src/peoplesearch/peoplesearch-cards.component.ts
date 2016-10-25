import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchService from './peoplesearch.service';
import Person from '../models/person.model';

declare var PWM_PS: any;

@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-cards.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-cards.component.html')
})
export default class PeopleSearchCardsComponent {
    private deregistrationCallback: () => void;
    people: Person[];

    static $inject = [ '$scope', 'PeopleSearchService' ];
    constructor(
        private $scope: IScope,
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
