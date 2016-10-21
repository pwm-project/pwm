export default class Person {
    userKey: string;
    photoUrl: string;
    orgChartParentKey: string;
    fields: any[];

    constructor(options: any) {
        this.fields = options.fields;
        this.orgChartParentKey = options.orgChartParentKey;
        this.photoUrl = options.photoUrl;
        this.userKey = options.userKey;
    }
}
