import Person from '../models/person.model';

export default class PeopleSearchService {
    private people: Person[];

    static $inject = ['$rootScope', '$timeout'];
    public constructor(private $rootScope, private $timeout) {
    }

    // public subscribe(subscribersScope, callback) {
    //     var deregistrationCallback = this.$rootScope.$on('peoplesearch-data-changed', callback);
    //     subscribersScope.$on('$destroy', deregistrationCallback);
    //
    //     if (this.people) {
    //         this.$timeout(() => this.notifyPeoplesearchDataChangedListeners(this.people));
    //     }
    // }
    //
    // private notifyPeoplesearchDataChangedListeners(data: any) {
    //     this.$rootScope.$emit('peoplesearch-data-changed', data);
    //     this.$rootScope.$apply();
    // }
    //
    // public updateData(data: any) {
    //     this.people = data;
    //     this.notifyPeoplesearchDataChangedListeners(data);
    // }
}
