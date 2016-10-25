export default class Person {
    detail: any;
    displayNames: string[];
    orgChartParentKey: string;
    photoURL: string;
    userKey: string;

    constructor(options: any) {
        this.detail = options.detail;
        this.displayNames = options.displayNames;
        this.orgChartParentKey = options.orgChartParentKey;
        this.photoURL = options.photoURL;
        this.userKey = options.userKey;
    }
}
