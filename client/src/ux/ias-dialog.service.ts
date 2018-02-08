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
    IHttpResponse,
    IHttpService,
    IPromise,
    IQService,
    IRootScopeService,
    IScope,
    ITemplateCacheService
} from 'angular';
import DialogComponent from './dialog.component';

interface IDialogScope extends IScope {
    $ctrl: DialogComponent;
    cancel: () => void;
    cancelText: string;
    close: () => void;
    okText: string;
    prompt: boolean;
    data: any;
    textContent: string;
    title: string;
}

interface IDialogOptions {
    cancel?: string;
    controller?: any;
    ok?: string;
    prompt?: boolean;
    response?: string;
    scope?: IScope;
    template?: string;
    templateUrl?: string;
    textContent?: string;
    title?: string;
    locals?: any;
}

export default class DialogService {
    private compiledDialogElement: IAugmentedJQuery;
    private dialogController: any;
    private dialogDeferred: IDeferred<any>;

    static $inject = [ '$compile', '$controller', '$document', '$http', '$q', '$rootScope', '$templateCache' ];
    constructor(private $compile: ICompileService,
                private $controller: IControllerService,
                private $document: IDocumentService,
                private $http: IHttpService,
                private $q: IQService,
                private $rootScope: IRootScopeService,
                private $templateCache: ITemplateCacheService) {
    }

    alert(options: IDialogOptions): IPromise<any> {
        options.cancel = null;
        options.ok = options.ok || 'OK';

        return this.open(options);
    }

    cancel(response?: any): void {
        this.dialogDeferred.reject(response);
        this.destroy();
    }

    close(response?: any): void {
        this.dialogDeferred.resolve(response);
        this.destroy();
    }

    confirm(options: IDialogOptions): IPromise<any> {
        options.cancel = options.cancel || 'No';
        options.ok = options.ok || 'Yes';

        return this.open(options);
    }

    private destroy() {
        this.compiledDialogElement.detach();
        this.dialogController = null;
        this.dialogDeferred = null;
    }

    open(options: IDialogOptions): IPromise<any> {
        let self = this;

        // Initialize scope
        let scope: IScope | any = options.scope ? options.scope.$new(false) : <IDialogScope>(this.$rootScope
            .$new(true));
        scope.cancel = () => { self.cancel(); };
        scope.cancelText = options.cancel;
        scope.close = () => { self.close(scope.data.response); };
        scope.okText = options.ok;
        scope.prompt = options.prompt;
        scope.data = { response: options.response };
        scope.textContent = options.textContent;
        scope.title = options.title;
        let locals = options.locals || {};
        locals.$scope = scope;

        // Instantiate controller if provided
        if (options.controller) {
            this.dialogController = this.$controller(options.controller, locals);
        }

        // Compile template
        this.loadTemplate(options)
            .then((template) => {
                self.compiledDialogElement = self.$compile(template)(locals.$scope);

                // Insert element into DOM
                element(self.$document.find('body')).append(self.compiledDialogElement);
            });

        this.dialogDeferred = this.$q.defer();
        return this.dialogDeferred.promise;
    }

    prompt(options: IDialogOptions): IPromise<any> {
        options.cancel = options.cancel || 'Cancel';
        options.ok = options.ok || 'OK';
        options.prompt = true;

        return this.open(options);
    }

    private loadTemplate(options: IDialogOptions): IPromise<string> {

        if (options.template) {
            return this.$q.resolve(options.template);
        }

        else if (options.templateUrl) {
            let template: string = this.$templateCache.get<string>(options.templateUrl);
            let self = this;

            if (template) {
                return this.$q.resolve(template);
            }

            return this.$http
                .get(options.templateUrl)
                .then((response: IHttpResponse<any>) => {
                    self.$templateCache.put(options.templateUrl, response.data);
                    return response.data;
                });
        }

        else {
            return this.$q.resolve(
                '<ias-dialog>' +
                '   <div class="ias-dialog-header">' +
                '       <div ng-if="!!title" class="ias-title">{{title}}</div>' +
                '   </div>' +
                '   <div class="ias-dialog-body">' +
                '       <div ng-if="!prompt">{{textContent}}</div>' +
                '       <div ng-if="prompt">' +
                '           <ias-input-container>' +
                '               <label for="response">{{textContent}}</label>' +
                '               <input id="response" name="response" type="text" ng-model="data.response">' +
                '           </ias-input-container>' +
                '       </div>' +
                '   </div>' +
                '   <div class="ias-actions">' +
                '      <mf-button ng-if="!!okText" ng-click="close()">{{okText}}</mf-button>' +
                '      <mf-button ng-if="!!cancelText" ng-click="cancel()">{{cancelText}}</mf-button>' +
                '   </div>' +
                '   <mf-icon-button class="ias-dialog-close-button" icon="close_thick" ng-click="cancel()">' +
                '   </mf-icon-button>' +
                '</ias-dialog>'
            );
        }
    }
}
