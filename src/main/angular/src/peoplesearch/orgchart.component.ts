import { Component } from '../component';
import { IQService } from 'angular';
import PeopleService from '../services/people.service';
import Person from '../models/person.model';

@Component({
    stylesheetUrl: require('peoplesearch/orgchart.component.scss'),
    templateUrl: require('peoplesearch/orgchart.component.html')
})
export default class OrgChartComponent {
    private person: Person;
    private managementChain: Person[];
    private directReports: Person[];
    private primaryPersonStatus: string;

    static $inject = ['$q', '$state', '$stateParams', 'PeopleService'];
    constructor(
        private $q: IQService,
        private $state: angular.ui.IStateService,
        private $stateParams: angular.ui.IStateParamsService,
        private peopleService: PeopleService) {
    }

    $onInit() {
        var personId: string = this.$stateParams['personId'];

        if (personId) {
            this.primaryPersonStatus = 'fetching';

            this.$q.all({
                directReports: this.peopleService.getDirectReports(personId),
                managementChain: this.peopleService.getManagementChain(personId),
                person: this.peopleService.getPerson(personId)
            })
            .then((data) => {
                this.directReports = data['directReports'];
                this.managementChain = data['managementChain'];
                this.person = data['person'];
            })
            .catch((result) => {
                console.log(result);
            })
            .finally(() => {
                this.primaryPersonStatus = undefined;
            });
        }
    }

    close() {
        this.$state.go('search.table');
    }

    selectPerson(userKey: string) {
        this.$state.go('orgchart', { personId: userKey });
    }
}
