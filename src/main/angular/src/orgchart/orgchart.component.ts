import { Component } from '../component';
import PeopleService from '../services/people.service';
import Person from '../models/person.model';

var templateUrl = require('orgchart/orgchart.component.html');

@Component({
    templateUrl: templateUrl
})
export default class OrgChartComponent {
    private person: Person;

    static $inject = ['$state', '$stateParams', 'PeopleService'];
    public constructor(
        private $state: angular.ui.IStateService,
        private $stateParams: angular.ui.IStateParamsService,
        private peopleService: PeopleService) {
    }

    public $onInit() {
        var personId: string = this.$stateParams['personId'];

        // if (personId) {
        //     this.fetchPerson(personId, (data) => {
        //         this.setPrimaryPerson(data);
        //     });
        // }
    }

    // private setPrimaryPerson(person: Person): void {
    //     this.primaryPerson = person;
    //
    //     this.addManagerRecursive(person.managementChain[0].id);
    //     this.addDirectReports(person.id);
    // }
    //
    // private addManagerRecursive(managerKey: string) {
    //     if (managerKey) {
    //         let manager: Person = { id: managerKey };
    //
    //         this.managementChain.push(manager);
    //
    //         this.fetchPerson(managerKey, (person: Person) => {
    //             manager.photoUrl = person.photoUrl;
    //             manager.fields = person.fields;
    //
    //             this.addManagerRecursive(person.managementChain[0].id);
    //         });
    //     }
    // }
    //
    // private addDirectReports(personId: string) {
    //     console.log('Adding direct reports...');
    //
    //     this.peopleService.getOrgChartData(personId).then((response) => {
    //         console.log('Fetched direct reports: ' + response.data);
    //     }, (response => {
    //         console.log(response.data);
    //     }));
    // }
    //
    // private fetchPerson(id, callback) {
    //     this.peopleService
    //         .getUserData(id)
    //         .then(
    //             (person: Person) => {
    //                 callback(person);
    //             },
    //             (response => {
    //                 console.log(response.data);
    //             }));
    // }

    public close() {
        this.$state.go('search.table');
    }
}
