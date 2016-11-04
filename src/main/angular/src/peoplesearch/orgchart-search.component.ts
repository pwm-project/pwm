import { Component } from '../component';
import { IPromise, IQService, IScope } from 'angular';
import { IPeopleService } from '../services/people.service';
import Person from '../models/person.model';

@Component({
    stylesheetUrl: require('peoplesearch/orgchart-search.component.scss'),
    templateUrl: require('peoplesearch/orgchart-search.component.html')
})
export default class OrgChartSearchComponent {
    directReports: Person[];
    managementChain: Person[];
    person: Person;

    static $inject = [ '$q', '$scope', '$state', '$stateParams', 'PeopleService' ];
    constructor(private $q: IQService,
                private $scope: IScope,
                private $state: angular.ui.IStateService,
                private $stateParams: angular.ui.IStateParamsService,
                private peopleService: IPeopleService) {
    }

    $onInit(): void {
        var self = this;

        // Retrieve data from the server
        this.fetchData();
    }

    autoCompleteSearch(query: string): IPromise<Person[]> {
        return this.peopleService.autoComplete(query);
    }

    onAutoCompleteItemSelected(person: Person): void {
        this.$state.go('orgchart', { personId: person.userKey });
    }

    private fetchData(): void {
        var personId: string = this.$stateParams['personId'];
        var self = this;

        // Fetch data
        if (personId) {
            this.$q.all({
                directReports: this.peopleService.getDirectReports(personId),
                managementChain: this.peopleService.getManagementChain(personId),
                person: this.peopleService.getPerson(personId)
            })
                .then((data) => {
                    this.$scope.$evalAsync(() => {
                        self.directReports = data['directReports'];
                        self.managementChain = data['managementChain'];
                        self.person = data['person'];
                    });
                })
                .catch((result) => {
                    console.log(result);
                });
        }
    }
}
