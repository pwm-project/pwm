/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
