export default class Person {
    // Common properties
    userKey: string;
    numOfDirectReports: number;

    // Autocomplete properties (via Search)
    displayName: string;

    // Details properties (not available in search)
    detail: any;
    displayNames: string[];
    photoURL: string;

    // Search properties (not available in details)
    givenName: string;
    mail: string;
    sn: string;
    telephoneNumber: string;
    title: string;

    constructor(options: any) {
        // Common properties
        this.userKey = options.userKey;

        // Autocomplete properties (via Search)
        this.displayName = options.displayName;

        // Details properties
        this.detail = options.detail;
        this.displayNames = options.displayNames;
        this.photoURL = options.photoURL;

        // Search properties
        this.givenName = options.givenName;
        this.mail = options.mail;
        this.sn = options.sn;
        this.telephoneNumber = options.telephoneNumber;
        this.title = options.title;
    }
}
