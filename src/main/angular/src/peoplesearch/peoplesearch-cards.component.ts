import { Component } from '../component';
import { DialogService } from '../ux/dialog.service';
import IPeopleService from '../services/people.service';
import { IScope } from 'angular';
import Person from '../models/person.model';
import PeopleSearchBaseComponent from './peoplesearch-base.component';


@Component({
    stylesheetUrl: require('peoplesearch/peoplesearch-cards.component.scss'),
    templateUrl: require('peoplesearch/peoplesearch-cards.component.html')
})
export default class PeopleSearchCardsComponent extends PeopleSearchBaseComponent {
    columnConfiguration: any;

    static $inject = [ '$scope', '$state', '$stateParams', 'PeopleService' ];
    constructor($scope: IScope,
                $state: angular.ui.IStateService,
                $stateParams: angular.ui.IStateParamsService,
                peopleService: IPeopleService) {
        super($scope, $state, $stateParams, peopleService);
    }

    $onInit(): void {
        super.$onInit();

        var self = this;

        // Fetch data when query changes
        this.$scope.$watch('$ctrl.query', (newValue: string) => {
            this.loading = true;

            if (!newValue) {
                self.people = [];
            }
            else {
                this.peopleService
                    .cardSearch(newValue)
                    .then((people: Person[]) => {
                        self.people = people;
                    })
                    .finally(() => {
                        this.loading = false;
                    });
            }
        });
    }

    gotoTableView() {
        super.gotoState('search.table');
    }

    selectPerson(person: Person): void {
        this.$state.go('search.cards.details', { personId: person.userKey, query: this.query });
    }
}
