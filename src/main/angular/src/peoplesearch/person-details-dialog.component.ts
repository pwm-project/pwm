import { Component } from '../component';
import { IPeopleService } from '../services/people.service';
import Person from '../models/person.model';

@Component({
    stylesheetUrl: require('peoplesearch/person-details-dialog.component.scss'),
    templateUrl: require('peoplesearch/person-details-dialog.component.html')
})
export default class PersonDetailsDialogComponent {
    person: Person;

    static $inject = [ '$state', '$stateParams', 'PeopleService' ];
    constructor(private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        var personId = this.$stateParams['personId'];

        this.peopleService
            .getPerson(personId)
            .then((person: Person) => {
                this.person = person;
            });
    }

    closeDialog(): void {
        this.$state.go('^', { query: this.$stateParams['query'] });
    }

    gotoOrgChart(): void {
        this.$state.go('orgchart.search', { personId: this.person.userKey });
    }

    getPersonDetailsUrl(personId: string): string {
        return this.$state.href('.', { personId: personId }, { inherit: true, });
    }

    searchText(text: string): void {
        this.$state.go('^', { query: text });
    }
}
