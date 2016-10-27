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
    private managerListStatus: string;

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

                if (this.directReports.length === 0 && this.managementChain.length === 0) {
                    let placeholderManager: Person = new Person({displayNames: ['Not Defined']});
                    this.managementChain.push(placeholderManager);
                    this.managerListStatus = 'disabled';
                }
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
        if (userKey) {
            this.$state.go('orgchart', {personId: userKey});
        }
    }
}
