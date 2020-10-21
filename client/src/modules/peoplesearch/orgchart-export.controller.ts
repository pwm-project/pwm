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

import {IPwmService} from '../../services/pwm.service';

require('./orgchart-export.component.scss');

export default class OrgchartExportController {
    depth = '1';

    static $inject = [
        '$window',
        'IasDialogService',
        'translateFilter',
        'PwmService',
        'maxDepth',
        'personName',
        'userKey'
    ];
    constructor(private $window: angular.IWindowService,
                private IasDialogService: any,
                private translateFilter: (id: string) => string,
                private pwmService: IPwmService,
                private maxDepth: number,
                private personName: string,
                private userKey: string) {
    }

    exportOrgChart() {
        this.$window.location.href = this.pwmService.getServerUrl('exportOrgChart', {
            depth: this.depth,
            userKey: this.userKey
        });

        this.IasDialogService.close();
    }
}
