import { Component } from '../component';
import { element, IScope, IWindowService } from 'angular';
import Person from '../models/person.model';

@Component({
    bindings: {
        directReports: '<',
        managementChain: '<',
        person: '<'
    },
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
        photoURL: null,
        userKey: null
    });

    private maxVisibleManagers: number;
    private windowWidth: number;
    private visibleManagers: Person[];

    static $inject = ['$scope', '$state', '$window'];
    constructor(
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $window: IWindowService) {
    }

    $onInit() {
        var self = this;

        this.updateLayout();

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
        this.$scope.$watch('$ctrl.maxVisibleManagers', () => {
            this.resetManagerList();
        });
    }

    getManagerCardSize() {
        return this.isWideLayout() ? 'small' : 'normal';
    }

    getManagementChain(): Person[] {
        // Display managers in a row
        if (this.isWideLayout()) {
            // All managers can fit on screen
            if (this.maxVisibleManagers >= this.managementChain.length) {
                return this.managementChain;
            }

            // Not all managers can fit on screen
            if (!this.visibleManagers) {
                // Show a blank manager as last manager in the chain in place of
                // the last visible manager. Blank manager links to the new last visible manager.
                this.visibleManagers = this.managementChain.slice(0, this.maxVisibleManagers - 1);
                var lastManager = this.managementChain[this.maxVisibleManagers - 2];

                this.visibleManagers.push(new Person({
                    userKey: lastManager.userKey,
                    photoURL: null,
                    displayNames: []
                }));
            }

            return this.visibleManagers;
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

    showingOverflow() {
        return this.visibleManagers &&
            this.managementChain &&
            this.visibleManagers.length < this.managementChain.length;
    }

    isPersonOrphan() {
        return !(this.hasDirectReports() || this.hasManagementChain());
    }

    selectPerson(userKey: string) {
        this.$state.go('orgchart', { personId: userKey });
    }

    private isWideLayout() {
        return this.windowWidth >= 490;
    }

    // Remove all displayed managers so the list is updated on window resize
    private resetManagerList() {
        this.visibleManagers = null;
    }

    private setMaxVisibleManagers() {
        this.maxVisibleManagers = Math.floor(
            (this.windowWidth - 115 /* left margin */) /
            125 /* card width + right margin */);
    }

    private setWindowWidth() {
        this.windowWidth = this.$window.innerWidth;
    }

    private updateLayout() {
        this.setWindowWidth();
        this.setMaxVisibleManagers();
    }
}
