/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {element, ICompileService, IScope, ITemplateCacheService, module} from 'angular';

/**
 * Angular controller for the configeditor.jsp page.  This class is used to transition the page from JSPs into
 * eventually a single page angular application.
 */
export default class ConfigEditorController {
    static $inject = ['$scope', '$compile'];
    constructor(
        private $scope: IScope,
        private $compile: ICompileService
    ) {
        $scope.$on('content-added', (event, elementId) => {
            this.digestNewContent(elementId);
        });
    }

    digestNewContent(elementId: string) {
        if (elementId) {
            const element = document.getElementById(elementId);

            if (element) {
                this.$compile(element)(this.$scope);
                this.$scope.$digest();
            }
        }
    }
}
