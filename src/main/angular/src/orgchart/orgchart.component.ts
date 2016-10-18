import { OrgChartService } from './orgchart.service';

declare var PWM_GLOBAL: any; // Comes from PWM

interface PersonInfo {
    userKey: string;
    photoUrl?: string;
    displayFields?: string[];
}

export class OrgChartComponent {
    public templateUrl = PWM_GLOBAL['url-context'] + '/public/resources/app/orgchart/orgchart.component.html';

    public controller = class {
        private primaryPerson: PersonInfo;
        private managementChain: PersonInfo[] = [];
        private directReports: PersonInfo[] = [];

        static $inject = ['$state', '$stateParams', 'orgChartService'];
        public constructor(private $state, private $stateParams, private orgChartService: OrgChartService) {
        }

        // Available controller life cycle methods are: $onInit, $onDestroy, $onChanges, $postLink
        public $onInit() {
            console.log("Org chart person user key: " + this.$stateParams.userKey);

            if (this.$stateParams.userKey) {
                this.fetchPersonInfo(this.$stateParams.userKey, (data) => {
                    this.setPrimaryPerson(data);
                });
            }
        }

        public $onDestroy() {
        }

        private setPrimaryPerson(data: any): void {
            this.primaryPerson = {
                userKey: data.userKey,
                photoUrl: data.photoURL,
                displayFields: data.displayNames
            };

            this.addManagerRecursive(data.orgChartParentKey);
            this.addDirectReports(data.userKey);
        }

        private addManagerRecursive(managerKey: string) {
            if (managerKey) {
                let manager: PersonInfo = {
                    userKey: managerKey
                };

                this.managementChain.push(manager);

                this.fetchPersonInfo(managerKey, (data) => {
                    manager.photoUrl = data.photoURL;
                    manager.displayFields = data.displayNames;

                    this.addManagerRecursive(data.orgChartParentKey);
                });
            }
        }

        private addDirectReports(userKey) {
            console.log("Adding direct reports...");

            this.orgChartService.getOrgChartData(userKey).then((response) => {
                console.log("Fetched direct reports: " + response.data);
            }, (response => {
                console.log(response.data);
            }));
        }

        private fetchPersonInfo(userKey, callback) {
            this.orgChartService.getUserData(userKey).then((response) => {
                callback(response.data['data']);
            }, (response => {
                console.log(response.data);
            }));
        }

        public close() {
            this.$state.go('search.table');
        }
    };
}
