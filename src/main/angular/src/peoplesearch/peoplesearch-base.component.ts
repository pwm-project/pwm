import { IPeopleService } from '../services/people.service';
import { IPromise, IScope } from 'angular';
import SearchResult from '../models/search-result.model';
import Person from '../models/person.model';

export default class PeopleSearchBaseComponent {
    loading: boolean;
    query: string;
    searchMessage: string;
    searchResult: SearchResult;

    constructor(protected $scope: IScope,
                protected $state: angular.ui.IStateService,
                protected $stateParams: angular.ui.IStateParamsService,
                protected peopleService: IPeopleService) {}

    $onInit(): void {
        this.query = this.$stateParams['query'];
    }

    gotoOrgchart(): void {
        this.$state.go('orgchart.index');
    }

    gotoState(state: string): void {
        this.$state.go(state, { query: this.query });
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

    protected fetchData(searchFunction: (query: string) => IPromise<SearchResult>) {
        const self = this;

        // Fetch data when query changes
        this.$scope.$watch('$ctrl.query', (newValue: string, oldValue: string) => {
            if (newValue === oldValue) {
                return;
            }

            self.setSearchMessage(null);

            if (!newValue) {
                self.searchResult = null;
            }
            else {
                searchFunction
                    .call(self.peopleService, newValue)
                    .then((searchResult: SearchResult) => {
                        self.searchResult = searchResult;
                        self.setSearchMessage(searchResult);
                    });
            }
        });
    }
}
