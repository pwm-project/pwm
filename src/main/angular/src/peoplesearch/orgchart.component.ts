import { Component } from '../component';
import { element, IQService, IWindowService } from 'angular';
import PeopleService from '../services/people.service';
import Person from '../models/person.model';
import { IScope } from 'angular';

@Component({
    stylesheetUrl: require('peoplesearch/orgchart.component.scss'),
    templateUrl: require('peoplesearch/orgchart.component.html')
})
export default class OrgChartComponent {
    directReports: Person[];
    managementChain: Person[];
    person: Person;
    emptyPerson: Person = new Person({
        displayNames: [
            'No Managers'
        ],
        photoURL: 'images/question_mark.png',
        userKey: null
    });

    private maxManagers: number;
    private windowWidth: number;
    private managers: Person[];

    static $inject = ['$q', '$scope', '$state', '$stateParams', '$window', 'PeopleService'];
    constructor(
        private $q: IQService,
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $stateParams: angular.ui.IStateParamsService,
        private $window: IWindowService,
        private peopleService: PeopleService) {
    }

    $onInit() {
        // Retrieve data from the server
        this.fetchData();
        this.updateLayout();

        var self = this;

        // OrgChartComponent has different functionality at different window widths. On window resize, we
        // want to update the state of the component and trigger a $digest
        // noinspection TypeScriptUnresolvedFunction
        element(this.$window).bind('resize', () => {
            self.updateLayout();
            self.$scope.$apply();
        });

        // In large displays managers are displayed in a row. Any time this property changes, we want
        // to force our manager list to be recalculated in this.getManagementChain() so it returns the correct
        // result at all window widths
        this.$scope.$watch('$ctrl.maxManagers', () => {
            this.resetManagerList();
        });
    }

    close() {
        this.$state.go('search.table');
    }

    getManagerCardSize() {
        return this.isWideLayout() ? 'small' : 'normal';
    }

    getManagementChain(): Person[] {
        // Display managers in a row
        if (this.isWideLayout()) {
            // All managers can fit on screen
            if (this.maxManagers >= this.managementChain.length) {
                return this.managementChain;
            }

            // Not all managers can fit on screen
            if (!this.managers) {
                // Show a blank manager as last manager in the chain in place of
                // the last visible manager. Blank manager links to the new last visible manager.
                this.managers = this.managementChain.slice(0, this.maxManagers - 1);
                var lastManager = this.managementChain[this.maxManagers - 2];

                this.managers.push(new Person({
                    userKey: lastManager.userKey,
                    photoURL: 'images/more.png',
                    displayNames: []
                }));
            }

            return this.managers;
        }

        // All managers can fit on screen in a column. Order is reversed for ease of rendering and style
        return [].concat(this.managementChain).reverse();
    }

    hasDirectReports() {
        return this.directReports && this.directReports.length;
    }

    hasManagementChain() {
        return this.managementChain && this.managementChain.length;
    }

    isPersonOrphan() {
        return !(this.hasDirectReports() || this.hasManagementChain());
    }

    selectPerson(userKey: string) {
        this.$state.go('orgchart', { personId: userKey });
    }

    private fetchData() {
        var personId: string = this.$stateParams['personId'];
        var self = this;

        // Fetch data
        if (personId) {
            this.$q.all({
                directReports: this.peopleService.getDirectReports(personId),
                managementChain: this.peopleService.getManagementChain(personId),
                person: this.peopleService.getPerson(personId)
            })
                .then((data) => {
                    this.$scope.$evalAsync(() => {
                        self.directReports = data['directReports'];
                        self.managementChain = data['managementChain'];
                        self.person = data['person'];
                    });
                })
                .catch((result) => {
                    console.log(result);
                });
        }
    }

    private isWideLayout() {
        return this.windowWidth >= 625;
    }

    // Remove all displayed managers so the list is updated on window resize
    private resetManagerList() {
        this.managers = null;
    }

    private setMaxManagers() {
        this.maxManagers = Math.floor(
            (this.windowWidth - 115 /* left margin */) /
            125 /* card width + right margin */);
    }

    private setWindowWidth() {
        this.windowWidth = this.$window.innerWidth;
    }

    private updateLayout() {
        this.setWindowWidth();
        this.setMaxManagers();
    }
}
