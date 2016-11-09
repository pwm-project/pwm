import Person from '../models/person.model';

export function FullNameFilter(): (person: Person) => string {
    return (person: Person): string => {
        return `${person.givenName} ${person.sn}`;
    };
}
