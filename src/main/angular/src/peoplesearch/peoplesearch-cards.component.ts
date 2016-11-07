import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchService from './peoplesearch.service';
import PeopleSearchBaseComponent from './peoplesearch-base.component';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-cards.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-cards.component.html')
})
export default class PeopleSearchCardsComponent extends PeopleSearchBaseComponent {
    static $inject = [ '$scope', 'PeopleSearchService' ];
    constructor(
        $scope: IScope,
        peopleSearchService: PeopleSearchService) {
        super($scope, peopleSearchService);
    }
}
