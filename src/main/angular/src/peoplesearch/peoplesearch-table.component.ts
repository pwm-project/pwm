import { Component } from '../component';
import { IScope } from 'angular';
import PeopleSearchBaseComponent from './peoplesearch-base.component';
import PeopleSearchService from './peoplesearch.service';
import { IConfigService } from '../services/config.service';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-table.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-table.component.html')
})
export default class PeopleSearchTableComponent extends PeopleSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [ '$scope', 'ConfigService', 'PeopleSearchService' ];
    constructor($scope: IScope,
                private configService: IConfigService,
                peopleSearchService: PeopleSearchService) {
        super($scope, peopleSearchService);

        var self = this;
        configService.getColumnConfiguration().then((columnConfiguration: any) => {
            self.columnConfiguration = columnConfiguration;
        });
    }
}
