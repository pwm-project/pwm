import { Component } from '../component';
import PeopleSearchService from './peoplesearch.service';
import Person from '../models/person.model';

var templateUrl = require('peoplesearch/peoplesearch-cards.component.html');

@Component({
    templateUrl: templateUrl
})
export default class PeopleSearchCardsComponent {
    people: Person[];

    static $inject = ['$scope', 'peopleSearchService'];
    public constructor(
        private $scope: angular.IScope,
        private peopleSearchService: PeopleSearchService) {
    }

    public $onInit() {
        // this.peopleSearchService.subscribe(this.$scope, (event, data) => this.dataChanged(data) );
    }

    // public dataChanged(data) {
    //     this.people = data;
    // }

    // public selectPerson(id: string) {
    //     // PWM_PS.showUserDetail(userKey);
    // }
}
