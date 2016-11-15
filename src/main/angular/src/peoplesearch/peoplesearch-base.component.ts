import { IPeopleService } from '../services/people.service';
import Person from '../models/person.model';
import { IScope } from 'angular';

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

    gotoOrgchart(): void {
        this.$state.go('orgchart.index');
    }

    gotoState(state: string): void {
        this.$state.go(state, { query: this.query });
    }

    // Trigger "No Results" message when search already done loading has no results
    showNoResults(): boolean {
        if (this.query && !this.loading) {
            return this.people.length === 0;
        }

        return false;
    }
}
