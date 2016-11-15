import Person from './person.model';

export default class SearchResult {
    sizeExceeded: boolean;
    people: Person[];

    constructor(options: any) {
        this.sizeExceeded = options.sizeExceeded;
        this.people = options.searchResults.map((person: any) => new Person(person));
    }
}
