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
    private person: Person;
    private emptyPerson: Person;
    private managementChain: Person[];
    private directReports: Person[];
    private windowWidth: number;

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

        // Setup listener on window resize
        // noinspection TypeScriptUnresolvedFunction
        element(this.$window).bind('resize', function() {
            self.setWindowWidth();
            self.$scope.$apply();
        });

        // Initialize windowWidth
        this.setWindowWidth();

        this.emptyPerson = new Person({
            displayNames: [
                'No Managers'
            ],
            photoURL: 'images/question_mark.png',
            userKey: null
        });
    }

    close() {
        this.$state.go('search.table');
    }

    getManagerCardSize() {
        return this.isWideLayout() ? 'small' : 'normal';
    }

    getManagementChain(): Person[] {
        if (this.isWideLayout()) {
            // return original data
            return this.managementChain;
        }

        // return reversed data
        return [].concat(this.managementChain).reverse();
    }

    hasDirectReports() {
        return this.directReports && this.directReports.length;
    }

    hasManagementChain() {
        return this.managementChain && this.managementChain.length;
    }

    isOrphan() {
        return !(this.hasDirectReports() || this.hasManagementChain());
    }

    selectPerson(userKey: string) {
        this.$state.go('orgchart', { personId: userKey });
    }

    setWindowWidth() {
        this.windowWidth = this.$window.innerWidth;
    }

    private isWideLayout() {
        return this.windowWidth >= 625;
    }
}
