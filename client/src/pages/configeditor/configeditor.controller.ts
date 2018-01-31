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
