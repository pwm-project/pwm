import { IScope } from 'angular';
import PeopleSearchService from './peoplesearch.service';
import Person from '../models/person.model';

declare var PWM_PS: any;

export default class PeopleSearchBaseComponent {
    private deregistrationCallback: () => void;
    people: Person[];

    constructor(
        protected $scope: IScope,
        protected peopleSearchService: PeopleSearchService) {
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
