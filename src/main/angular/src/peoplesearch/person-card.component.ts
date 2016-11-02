import { Component } from '../component';
import Person from '../models/person.model';
import { IPeopleService } from '../services/people.service';

@Component({
    bindings: {
        directReports: '<',
        person: '<',
        size: '@',
        showDirectReportCount: '@'
    },
    stylesheetUrl: require('peoplesearch/person-card.component.scss'),
    templateUrl: require('peoplesearch/person-card.component.html')
})
export default class PersonCardComponent {
    private data: string[];
    private details: any[]; // For large style cards
    private person: Person;
    private directReports: Person[];
    private size: string;
    private showDirectReportCount: boolean;

    static $inject = ['PeopleService'];
    constructor(private peopleService: IPeopleService) {
    }

    $onInit() {
        this.details = [];

        if (this.showDirectReportCount) {
            this.peopleService.getNumberOfDirectReports(this.person.userKey)
                .then((numOfDirectReports) => {
                    this.person.numOfDirectReports = numOfDirectReports;
                }).catch((result) => {
                console.log(result);
            });
        }
    }

    $onChanges() {
        if (this.person) {
            this.setDisplayData();
        }
    }

    getAvatarStyle(): any {
        if (this.person && this.person.photoURL) {
            return {
                'background-image': 'url(' + this.person.photoURL + ')'
            };
        }

        return {};
    }

    private setDisplayData() {
        // This data is only available on people search views
        if (this.person.givenName && this.person.sn) {
            this.data = [
                this.person.givenName + ' ' + this.person.sn,
                this.person.title,
                this.person.telephoneNumber,
                this.person.mail,
            ];
        }
        // This data is only available on details and orgchart views
        else {
            this.data = this.person.displayNames;
        }

        // This data is only available on details and orgchart views
        if (this.person.detail) {
            this.details = Object
                .keys(this.person.detail)
                .map((key: string) => { return this.person.detail[key]; });
        }

        if (this.directReports) {
            this.person.numOfDirectReports = this.directReports.length;
        }
    }
}
