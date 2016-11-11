import Person from './person.model';
export default class OrgChartData {

    constructor(public manager: Person,
                public children: Person[],
                public self: Person) {}

}
