/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

var PWM_GUEST = PWM_GUEST || {};
var PWM_VAR = PWM_VAR || {};


PWM_GUEST.initDatePicker = function(maxValidDate,selectedDate) {
    require(["dijit/form/DateTextBox"],function(DateTextBox){
        new DateTextBox({
            constraints: {
                min: new Date(),
                max: maxValidDate
            },
            value: selectedDate,
            onChange: function(){
                var selectedDate = new Date(Date.parse(this.value));
                var isoDate = selectedDate.toISOString();
                PWM_MAIN.getObject('_expirationDateFormInput').value = isoDate;
            }
        }, "expiredate-stub");
    });

};


