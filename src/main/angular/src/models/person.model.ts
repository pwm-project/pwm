export default class Person {
    detail: any;
    displayNames: string[];
    orgChartParentKey: string;
    photoURL: string;
    userKey: string;
    givenName: string;
    sn: string;
    title: string;
    mail: string;
    telephoneNumber: string;

    constructor(options: any) {
        this.detail = options.detail;
        this.displayNames = options.displayNames;
        this.orgChartParentKey = options.orgChartParentKey;
        this.photoURL = options.photoURL;
        this.userKey = options.userKey;
        this.givenName = options.givenName;
        this.sn = options.sn;
        this.title = options.title;
        this.mail = options.mail;
        this.telephoneNumber = options.telephoneNumber;
    }
}
