import {
    element,
    IAugmentedJQuery,
    ICompileService,
    IControllerService,
    IDeferred,
    IDocumentService,
    IPromise,
    IQService,
    IRootScopeService,
    IScope,
    ITemplateRequestService,
    merge
} from 'angular';

export class Dialog {
    clickOutsideToClose?: boolean;
    controller?: string;
    element: IAugmentedJQuery;
    locals?: any;
    parent?: IAugmentedJQuery;
    parentScope?: IScope;
    scope: IScope;
    templateUrl: string;

    constructor(options: any) {
        this.clickOutsideToClose = options.clickOutsideToClose;
        this.controller = options.controller;
        this.locals = options.locals;
        this.parent = options.parent;
        this.parentScope = options.parentScope;
        this.templateUrl = options.templateUrl;
    }
}

export class DialogService {
    private deferred: IDeferred<any>;
    private dialog: Dialog;

    static $inject = [ '$compile', '$controller', '$document', '$q', '$rootScope', '$templateRequest' ];
    constructor(private $compile: ICompileService,
                private $controller: IControllerService,
                private $document: IDocumentService,
                private $q: IQService,
                private $rootScope: IRootScopeService,
                private $templateRequest: ITemplateRequestService) {}

    close(): void {
        this.destroyDialog();
        this.deferred.reject();
    }

    openDialog() {
        var self = this;
        var parent: IAugmentedJQuery = this.dialog.parent || this.$document.find('body');
        this.dialog.scope = this.$rootScope.$new(false, this.dialog.parentScope);

        this.$templateRequest(this.dialog.templateUrl)
            .then((html: string) => {
                // Create and append element to DOM
                self.dialog.element = element(html);
                parent.append(self.dialog.element);

                self.$compile(self.dialog.element)(self.dialog.scope);
                var controller = self.$controller(self.dialog.controller, { $scope: self.dialog.scope });

                // Assign locals to constructor
                merge(controller, self.dialog.locals);
            });
    }

    destroyDialog() {
        this.dialog.scope.$destroy();
        this.dialog.scope = null;

        this.dialog.element.detach();
        this.dialog.element = null;

        this.dialog = null;
    }

    show(dialog: Dialog): IPromise<any> {
        if (this.dialog) {
            this.close();
        }
        this.dialog = dialog;
        this.deferred = this.$q.defer();

        this.openDialog();

        return this.deferred.promise;
    }
}
