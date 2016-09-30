declare var PWM_GLOBAL: any; // Comes from PWM

export class OrgChartComponent {
    public templateUrl = PWM_GLOBAL['url-context'] + '/public/resources/app/orgchart/orgchart.component.html';

    public controller = class {
        private person: any;

        static $inject = ['$state', 'orgChartService'];
        public constructor(private $state, private orgChartService) {
        }

        // Available controller life cycle methods are: $onInit, $onDestroy, $onChanges, $postLink
        public $onInit() {
            this.orgChartService.getOrgChartData().then((response) => {
                this.person = response.data.person;
            }, (response => {
                console.log(response.data);
            }));
        }

        public $onDestroy() {
        }

        public close() {
            this.$state.go('search.table');
        }
    };
}
