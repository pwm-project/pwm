declare var PWM_GLOBAL: any;
declare var PWM_PS: any;

export class PeopleSearchTableComponent {
    public templateUrl = PWM_GLOBAL['url-context'] + '/public/resources/app/peoplesearch/peoplesearch-table.component.html';

    public controller = class {
        data: any;

        static $inject = ['$scope', '$timeout', 'peopleSearchService'];
        public constructor(private $scope, private $timeout, private peopleSearchService) {
        }

        // Available controller life cycle methods are: $onInit, $onDestroy, $onChanges, $postLink
        public $onInit() {
            this.peopleSearchService.subscribe(this.$scope, (event, data) => { this.dataChanged(data) });
        }

        public $onDestroy() {
        }

        private dataChanged(newData) {
            this.data = newData;
        }

        public selectPerson(userKey) {
            PWM_PS.showUserDetail(userKey);
        }
    };
}