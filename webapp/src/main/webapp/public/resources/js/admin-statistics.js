/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

const PWM_ADMIN_STATISTICS = {};

import {PWM_JSLibrary} from "./jslibrary.js";
import {PWM_UILibrary} from "./uilibrary.js";
import {PWM_MAIN} from "./main.js";

export {PWM_ADMIN_STATISTICS};

PWM_ADMIN_STATISTICS.initStatisticsPage=function() {
    PWM_MAIN.addEventHandler('statsPeriodForm','change',function() {
        PWM_ADMIN_STATISTICS.refreshStatistics();
    });

    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','readKeys');
    const loadFunction = function(data) {
        const selectElement = PWM_JSLibrary.getElement('statsPeriodSelect');

        if (data['data'] && data['data']) {
            const keys = data['data'];
            let optionsHtml = '';
            PWM_JSLibrary.forEachInObject(keys, function (key, value) {
                optionsHtml += '<option value="' + key + '">' + value + '</option>';
            });
            selectElement.innerHTML = optionsHtml;
        }

        PWM_ADMIN_STATISTICS.refreshStatistics();
    };
    PWM_MAIN.ajaxRequest(url,loadFunction);
};

PWM_ADMIN_STATISTICS.refreshStatistics=async function() {
    const waitInnerHtml = '<tr><td colspan="2">'
        + await PWM_MAIN.getDisplayString('Display_PleaseWait')
        + '</td></tr>'

    const tableElement = PWM_JSLibrary.getElement('statisticsTable');
    const averageTableElement = PWM_JSLibrary.getElement('averageStatisticsTable');

    tableElement.innerHTML = waitInnerHtml;
    averageTableElement.innerHTML = waitInnerHtml;

    const currentStatKey = PWM_JSLibrary.readValueOfSelectElement('statsPeriodSelect');

    let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','readStatistics');
    if ( currentStatKey ) {
        url = PWM_MAIN.addParamToUrl(url, 'statKey',currentStatKey);
    }

    const loadFunction = function(data) {

        if (data['data'] && data['data']['statistics']) {
            const fields = data['data']['statistics'];
            tableElement.innerHTML = PWM_UILibrary.displayElementsToTableContents(fields);
            PWM_UILibrary.initElementsToTableContents(fields);
        }
        if (data['data'] && data['data']['averageStatistics']) {
            const fields = data['data']['averageStatistics'];
            averageTableElement.innerHTML = PWM_UILibrary.displayElementsToTableContents(fields);
            PWM_UILibrary.initElementsToTableContents(fields);
        }
    };
    PWM_MAIN.ajaxRequest(url,loadFunction);
};
