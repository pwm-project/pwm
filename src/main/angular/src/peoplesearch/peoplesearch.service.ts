export class PeopleSearchService {
    private data: any;

    static $inject = ['$rootScope', '$timeout'];
    public constructor(private $rootScope, private $timeout) {
    }

    public subscribe(subscribersScope, callback) {
        var deregistrationCallback = this.$rootScope.$on('peoplesearch-data-changed', callback);
        subscribersScope.$on('$destroy', deregistrationCallback);

        if (this.data) {
            this.$timeout(() => this.notifyPeoplesearchDataChangedListeners(this.data));
        }
    }

    private notifyPeoplesearchDataChangedListeners(data: any) {
        this.$rootScope.$emit('peoplesearch-data-changed', data);
        this.$rootScope.$apply();
    }

    public updateData(data: any) {
        this.data = data;
        this.notifyPeoplesearchDataChangedListeners(data);
    }
}