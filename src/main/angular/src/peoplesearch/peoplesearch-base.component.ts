import { IScope } from 'angular';
import Person from '../models/person.model';
import {IPeopleService} from '../services/people.service';

declare var PWM_PS: any;

export default class PeopleSearchBaseComponent {
    people: Person[];
    query: string;

    constructor(protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected peopleService: IPeopleService) {}

    $onInit(): void {
        this.query = this.$stateParams['query'];
    }

    selectPerson(person: Person) {
        PWM_PS.showUserDetail(person.userKey);
    }

    gotoState(state: string) {
        this.$state.go(state, { query: this.query });
    }
}
