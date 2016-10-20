import { Component } from '../component';
import PeopleSearchService from './peoplesearch.service';
import Person from '../models/person.model';

var templateUrl = require('peoplesearch/peoplesearch-table.component.html');

@Component({
    templateUrl: templateUrl
})
export default class PeopleSearchTableComponent {
    people: Person[];

    static $inject = ['$scope', 'peopleSearchService'];
    public constructor(
        private $scope: angular.IScope,
        private peopleSearchService: PeopleSearchService) {
    }

    public $onInit() {
        // this.peopleSearchService.subscribe(this.$scope, (event, data) => this.dataChanged(data));
    }

    // private dataChanged(data) {
    //     this.people = data;
    // }
    //
    // public selectPerson(id: string) {
    //     // PWM_PS.showUserDetail(userKey);
    // }
}
