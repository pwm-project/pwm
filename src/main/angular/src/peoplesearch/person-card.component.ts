import { Component } from '../component';
import Person from '../models/person.model';


@Component({
    bindings: {
        person: '<'
    },
    stylesheetUrl: require('peoplesearch/person-card.component.scss'),
    templateUrl: require('peoplesearch/person-card.component.html')
})
export default class PersonCardComponent {
    private person: Person;
    private data: string[];

    static $inject = [];
    constructor() {
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
    }
}
