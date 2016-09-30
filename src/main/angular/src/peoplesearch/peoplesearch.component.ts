declare var PWM_GLOBAL: any;

export class PeopleSearchComponent {
    public templateUrl = PWM_GLOBAL['url-context'] + '/public/resources/app/peoplesearch/peoplesearch.component.html';

    public controller = class {
        viewToggleClass: string;

        static $inject = ['$state'];
        public constructor(private $state) {
        }

        // Available controller life cycle methods are: $onInit, $onDestroy, $onChanges, $postLink
        public $onInit() {
            if (this.$state.is('search.table')) {
                this.viewToggleClass = 'fa fa-th-large';
            } else {
                this.viewToggleClass = 'fa fa-list-alt';
            }
        }

        public $onDestroy() {
        }

        private viewToggleClicked() {
            if (this.$state.is('search.table')) {
                this.$state.go('search.cards');
                this.viewToggleClass = 'fa fa-list-alt';
            } else {
                this.$state.go('search.table');
                this.viewToggleClass = 'fa fa-th-large';
            }
        }
    };
}