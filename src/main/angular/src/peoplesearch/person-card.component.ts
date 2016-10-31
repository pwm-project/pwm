import { Component } from '../component';
import Person from '../models/person.model';
import { IPeopleService } from '../services/people.service';


@Component({
    bindings: {
        directReports: '<',
        person: '<',
        size: '@'
    },
    stylesheetUrl: require('peoplesearch/person-card.component.scss'),
    templateUrl: require('peoplesearch/person-card.component.html')
})
export default class PersonCardComponent {
    private data: string[];
    private details: any[]; // For large style cards
    private person: Person;
    private size: string;

    static $inject = [];
    constructor() {
    }

    $onInit() {
        this.details = [];
    }

    $onChanges() {
        if (this.person) {
            this.setDisplayData();
        }
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
    }
}
