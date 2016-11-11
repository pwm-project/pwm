import { Component } from '../component';
import { IAugmentedJQuery, ICompileService, IScope, ITimeoutService } from 'angular';

@Component({
    bindings: {
        autoFocus: '@',
        placeholder: '<',
        searchText: '='
    },
    templateUrl: require('ux/search-bar.component.html'),
    stylesheetUrl: require('ux/search-bar.component.scss')
})
export default class SearchBarComponent {
    autoFocus: boolean;
    searchText: string;

    static $inject = [ '$compile', '$element', '$scope', '$timeout' ];
    constructor(private $compile: ICompileService,
                private $element: IAugmentedJQuery,
                private $scope: IScope,
                private $timeout: ITimeoutService) {
    }

    $onInit(): void {
        var self = this;

        this.autoFocus = this.autoFocus !== undefined;

        if (this.autoFocus) {
            this.$timeout(() => {
                self.focusInput();
            }, 100);
        }
    }

    clearSearchText(): void {
        this.searchText = '';
        this.focusInput();
    }

    focusInput() {
        this.$element.find('input')[0].focus();
    }

    onInputKeyDown(event: KeyboardEvent): void {
        switch (event.keyCode) {
            case 27: // Escape
                this.clearSearchText();

                break;
        }
    }
}
