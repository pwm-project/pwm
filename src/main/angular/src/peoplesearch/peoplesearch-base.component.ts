import { IPeopleService } from '../services/people.service';
import { IPromise, IScope } from 'angular';
import SearchResult from '../models/search-result.model';
import Person from '../models/person.model';

interface ISearchFunction {
    (query: string): IPromise<SearchResult>;
}

export default class PeopleSearchBaseComponent {
    query: string;
    searchFunction: ISearchFunction;
    searchMessage: string;
    searchResult: SearchResult;

    protected constructor(protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected peopleService: IPeopleService) {}

    gotoOrgchart(): void {
        this.$state.go('orgchart.index');
    }

    gotoState(state: string): void {
        this.$state.go(state, { query: this.query });
    }

    initialize(searchFunction: ISearchFunction): void {
        this.query = this.$stateParams['query'];
        this.searchFunction = searchFunction;

        const self = this;

        // Fetch data when query changes
        this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            if (newValue === oldValue) {
                return;
            }

            self.setSearchMessage(null);
            self.fetchData();
        });

        this.fetchData();
    }

    selectPerson(person: Person): void {
        this.$state.go('.details', { personId: person.userKey, query: this.query });
    }

    protected setSearchMessage(searchResult: SearchResult) {
        if (!searchResult) {
            this.searchMessage = null;
            return;
        }

        if (searchResult.sizeExceeded) {
            this.searchMessage = `Only showing ${searchResult.people.length} results`;
        }
        if (!searchResult.people.length) {
            this.searchMessage = 'No results';
        }
    }

    protected fetchData(): void {
        const self = this;

        if (!this.query) {
            self.searchResult = null;
            return;
        }

        this.searchFunction
            .call(this.peopleService, this.query)
            .then((searchResult: SearchResult) => {
                self.searchResult = searchResult;
                self.setSearchMessage(searchResult);
            });
    }
}
