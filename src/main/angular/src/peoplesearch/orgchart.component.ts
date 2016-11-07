import { Component } from '../component';
import { element, IAugmentedJQuery, IFilterService, IScope, IWindowService } from 'angular';
import Person from '../models/person.model';

export enum OrgChartSize {
    ExtraSmall = 0,
    Small = 365,
    Medium = 410,
    Large = 454,
    ExtraLarge = 480
}

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
    elementWidth: number;
    managementChain: Person[];
    person: Person;

    emptyPerson: Person = new Person({
        displayNames: [
            'No Managers'
        ],
        photoURL: null,
        userKey: null
    });

    private elementSize: OrgChartSize = OrgChartSize.ExtraSmall;
    private maxVisibleManagers: number;
    private visibleManagers: Person[];

    static $inject = [ '$element', '$filter', '$scope', '$state', '$window' ];
    constructor(
        private $element: IAugmentedJQuery,
        private $filter: IFilterService,
        private $scope: IScope,
        private $state: angular.ui.IStateService,
        private $window: IWindowService) {
    }

    $onDestroy(): void {
        element(this.$window).off();
    }

    $onInit(): void {
        var self = this;

        this.updateLayout();

        // OrgChartComponent has different functionality at different widths. On element resize, we
        // want to update the state of the component and trigger a $digest
        element(this.$window).on('resize', () => {
            self.elementWidth = self.getElementWidth();
            self.$scope.$apply();
        });
        this.$scope.$watch('$ctrl.elementWidth', () => {
            self.updateLayout();
        });

        // In large displays managers are displayed in a row. Any time this property changes, we want
        // to force our manager list to be recalculated in this.getManagementChain() so it returns the correct
        // result at all element widths
        this.$scope.$watch('$ctrl.maxVisibleManagers', () => {
            this.resetManagerList();
        });
    }

    getManagerCardSize(): string {
        return this.isExtraLargeLayout() ? 'small' : 'normal';
    }

    getManagementChain(): Person[] {
        // Display managers in a row
        if (this.isExtraLargeLayout()) {
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

    hasDirectReports(): boolean {
        return this.directReports && !!this.directReports.length;
    }

    hasManagementChain(): boolean {
        return this.managementChain && !!this.managementChain.length;
    }

    isPersonOrphan(): boolean {
        return !(this.hasDirectReports() || this.hasManagementChain());
    }

    selectPerson(userKey: string): void {
        this.$state.go('orgchart', { personId: userKey });
    }

    showingOverflow(): boolean {
        return this.visibleManagers &&
            this.managementChain &&
            this.visibleManagers.length < this.managementChain.length;
    }

    private getElementWidth() {
        return this.$element[0].clientWidth;
    }

    private isExtraLargeLayout(): boolean {
        return this.elementSize === OrgChartSize.ExtraLarge;
    }

    // Remove all displayed managers so the list is updated on element resize
    private resetManagerList(): void {
        this.visibleManagers = null;
    }

    private setElementClass(): void {
        var className: string = [
            OrgChartSize.Small,
            OrgChartSize.ExtraSmall,
            OrgChartSize.Medium,
            OrgChartSize.Large,
            OrgChartSize.ExtraLarge
        ]
            .filter((size: OrgChartSize): boolean => {
                return size <= this.elementSize;
            })
            .map((size: OrgChartSize): string => {
                return this.$filter<(input: string) => string>('dasherize')(OrgChartSize[size]);
            })
            .join(' ');

        this.$element[0].className = '';
        this.$element.addClass(className);
    }

    private setElementSize(): void {
        var elementWidth: number = this.getElementWidth();

        if (elementWidth < OrgChartSize.Small) {
            this.elementSize = OrgChartSize.ExtraSmall;
        }
        else if (elementWidth < OrgChartSize.Medium) {
            this.elementSize = OrgChartSize.Small;
        }
        else if (elementWidth < OrgChartSize.Large) {
            this.elementSize = OrgChartSize.Medium;
        }
        else if (elementWidth < OrgChartSize.ExtraLarge) {
            this.elementSize = OrgChartSize.Large;
        }
        else {
            this.elementSize = OrgChartSize.ExtraLarge;
        }
    }

    private setMaxVisibleManagers(): void {
        this.maxVisibleManagers = Math.floor(
            (this.getElementWidth() - 115 /* left margin */) / 125 /* card width + right margin */);
    }

    private updateLayout(): void {
        this.setElementSize();
        this.setElementClass();
        this.setMaxVisibleManagers();
    }
}
