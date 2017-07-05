/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


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
