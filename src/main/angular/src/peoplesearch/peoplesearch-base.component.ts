import { IScope } from 'angular';
import Person from '../models/person.model';
import {IPeopleService} from '../services/people.service';

declare var PWM_PS: any;

export default class PeopleSearchBaseComponent {
    people: Person[] = [];
    query: string;
    loading: boolean;

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

    // Trigger "No Results" message when search already done loading has no results
    noSearchResults(): boolean {
        if (this.loading && this.query && this.query.length) {
            return (this.people.length == 0);
        }

        return false;
    }

    gotoOrgchart() {
        this.$state.go('orgchart.index');
    }

    gotoState(state: string) {
        this.$state.go(state, { query: this.query });
    }
}
