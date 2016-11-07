import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import PeopleSearchService from './peoplesearch.service';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-table.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-table.component.html')
})
export default class PeopleSearchTableComponent extends PeopleSearchBaseComponent {
    static $inject = [ '$scope', 'PeopleSearchService' ];
    constructor(
        $scope: IScope,
        peopleSearchService: PeopleSearchService) {
        super($scope, peopleSearchService);
    }
}
