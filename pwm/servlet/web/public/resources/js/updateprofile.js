/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

"use strict";

var PWM_GLOBAL = PWM_GLOBAL || {};
var PWM_MAIN = PWM_MAIN || {};
var PWM_VAR = PWM_VAR || {};

var PWM_UPDATE = PWM_UPDATE || {};

PWM_UPDATE.validateForm = function() {
    var validationProps = new Array();
    validationProps['serviceURL'] = PWM_MAIN.addPwmFormIDtoURL("UpdateProfile" + "?processAction=validate");
    validationProps['readDataFunction'] = function(){
        var paramData = { };
        for (var j = 0; j < document.forms.length; j++) {
            for (var i = 0; i < document.forms[j].length; i++) {
                var current = document.forms[j].elements[i];
                paramData[current.name] = current.value;
            }
        }
        return paramData;
    };
    validationProps['processResultsFunction'] = function(data){
        data = data['data'];
        if (data["success"] == "true") {
            PWM_MAIN.getObject("submitBtn").disabled = false;
            PWM_MAIN.showSuccess(data["message"]);
        } else {
            PWM_MAIN.getObject("submitBtn").disabled = true;
            PWM_MAIN.showError(data['message']);
        }
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};

