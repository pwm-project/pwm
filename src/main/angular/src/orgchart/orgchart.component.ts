import { Component } from '../component';
import { IQService } from 'angular';
import PeopleService from '../services/people.service';
import Person from '../models/person.model';

@Component({
    stylesheetUrl: require('orgchart/orgchart.component.scss'),
    templateUrl: require('orgchart/orgchart.component.html')
})
export default class OrgChartComponent {
    private person: Person;
    private managementChain: Person[];
    private directReports: Person[];

    static $inject = ['$q', '$state', '$stateParams', 'PeopleService'];
    public constructor(
        private $q: IQService,
        private $state: angular.ui.IStateService,
        private $stateParams: angular.ui.IStateParamsService,
        private peopleService: PeopleService) {
    }

    public $onInit() {
        var personId: string = this.$stateParams['personId'];

        if (personId) {
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
            });
        }
    }

    public close() {
        this.$state.go('search.table');
    }
}
