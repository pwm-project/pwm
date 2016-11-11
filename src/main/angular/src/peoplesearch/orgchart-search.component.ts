import { Component } from '../component';
import { IPromise, IQService, IScope } from 'angular';
import { IPeopleService } from '../services/people.service';
import Person from '../models/person.model';
import OrgChartData from '../models/orgchart-data.model';

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

        var personId: string = this.$stateParams['personId'];

        this.fetchOrgChartData(personId)
            .then((orgChartData: OrgChartData) => {
                // Override personId in case it was undefined
                personId = orgChartData.self.userKey;

                self.$q.all({
                    directReports: self.peopleService.getDirectReports(personId),
                    managementChain: self.peopleService.getManagementChain(personId),
                    person: self.peopleService.getPerson(personId)
                })
                .then((data) => {
                    self.$scope.$evalAsync(() => {
                        self.directReports = data['directReports'];
                        self.managementChain = data['managementChain'];
                        self.person = data['person'];
                    });
                })
                .catch((result) => {
                    console.log(result);
                });
            });
    }

    autoCompleteSearch(query: string): IPromise<Person[]> {
        return this.peopleService.autoComplete(query);
    }

    gotoSearchState(state: string) {
        this.$state.go(state);
    }

    onAutoCompleteItemSelected(person: Person): void {
        this.$state.go('orgchart.search', { personId: person.userKey });
    }

    private fetchOrgChartData(personId): IPromise<OrgChartData> {
        return this.peopleService.getOrgChartData(personId);
    }
}
