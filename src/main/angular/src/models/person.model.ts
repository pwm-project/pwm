export default class Person {
    directReports: Person[];
    fields: string[];
    givenName: string;
    mail: string;
    managementChain: Person[];
    numOfReports: number;
    orgChartParentKey: string;
    photoUrl: string;
    sn: string;
    telephoneNumber: string;
    title: string;
    userKey: string;

    constructor(options: any) {
        this.directReports = options.directReports;
        this.fields = options.fields;
        this.givenName = options.givenName;
        this.mail = options.mail;
        this.managementChain = options.managementChain;
        this.numOfReports = options.numOfReports;
        this.orgChartParentKey = options.orgChartParentKey;
        this.photoUrl = options.photoUrl;
        this.sn = options.sn;
        this.telephoneNumber = options.telephoneNumber;
        this.title = options.title;
        this.userKey = options.userKey;
    }
}
