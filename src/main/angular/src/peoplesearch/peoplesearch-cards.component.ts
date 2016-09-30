declare var PWM_GLOBAL: any;
declare var PWM_PS: any;

export class PeopleSearchCardsComponent {
    public templateUrl = PWM_GLOBAL['url-context'] + '/public/resources/app/peoplesearch/peoplesearch-cards.component.html';

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

        public dataChanged(newData) {
            this.data = newData;
        }

        public selectPerson(userKey) {
            PWM_PS.showUserDetail(userKey);
        }
    };
}
