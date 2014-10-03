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

var PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
var PARAM_QUESTION_PREFIX = "PwmResponse_Q_";

var PWM_RESPONSES = PWM_RESPONSES || {};
var PWM_VAR = PWM_VAR || {};

PWM_VAR['simpleRandomSelectElements'] = {};
PWM_VAR['simpleRandomOptions'] = [];


// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
PWM_RESPONSES.validateResponses=function() {
    require(["dojo/dom-form"], function(domForm){
        var serviceUrl = PWM_GLOBAL['url-setupresponses'] + "?processAction=validateResponses";
        if (PWM_GLOBAL['responseMode']) {
            serviceUrl += "&responseMode=" + PWM_GLOBAL['responseMode'];
        }
        var validationProps = {};
        validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingResponses');
        validationProps['serviceURL'] = serviceUrl;
        validationProps['readDataFunction'] = function(){
            return domForm.toObject('setupResponses');
        };
        validationProps['processResultsFunction'] = function(data){
            PWM_RESPONSES.updateDisplay(data['data']);
        };

        PWM_MAIN.pwmFormValidator(validationProps);
    });
};

PWM_RESPONSES.updateDisplay=function(resultInfo) {
    if (resultInfo == null) {
        PWM_MAIN.getObject("setresponses_button").disabled = false;
        return;
    }

    var result = resultInfo["message"];

    if (resultInfo["success"] == true) {
        PWM_MAIN.getObject("setresponses_button").disabled = false;
        PWM_MAIN.showSuccess(result);
    } else {
        PWM_MAIN.getObject("setresponses_button").disabled = true;
        PWM_MAIN.showError(result);
    }
};

PWM_RESPONSES.makeSelectOptionsDistinct=function() {
    require(["dojo","dijit/registry","dojo/_base/array","dojo/on","dojo/data/ObjectStore","dojo/store/Memory"],
        function(dojo,registry,array,dojoOn,ObjectStore,Memory){
            var startTime = (new Date()).getTime();
            console.log('entering makeSelectOptionsDistinct()');

            // cancel all the existing onchange events so they dont trigger while this function manipulates the select elements
            if (PWM_GLOBAL['randomSelectEventHandlers']) {
                for (var eventHandler in PWM_GLOBAL['randomSelectEventHandlers']) {
                    (function(eventHandler){
                        PWM_GLOBAL['randomSelectEventHandlers'][eventHandler].remove();
                    }(eventHandler));
                }
            }
            PWM_GLOBAL['randomSelectEventHandlers'] = new Array();

            // all possible random questions (populated by the jsp)
            var allPossibleTexts = PWM_VAR['simpleRandomOptions'];

            // string that is used at the top of unconfigured select list
            var initialChoiceText = PWM_MAIN.showString('Display_SelectionIndicator');

            // the HTML select elements (populated by the jsp)
            var simpleRandomSelectElements = PWM_VAR['simpleRandomSelectElements'];

            // texts that are in use
            var currentlySelectedTexts = [];

            for (var responseID in simpleRandomSelectElements) {
                (function(responseID){
                    var selectWidget = registry.byId(responseID);
                    var selectedValue = selectWidget.get('value');
                    currentlySelectedTexts.push(selectedValue);
                }(responseID));
            }

            // repopulate the select elements
            for (var responseID in simpleRandomSelectElements) {
                (function(responseID){
                    var questionID = simpleRandomSelectElements[responseID];
                    var selectWidget = registry.byId(responseID);
                    var selectedValue = selectWidget.get('value');
                    var dataOptions = [];
                    if (selectedValue == 'UNSELECTED') {
                        PWM_MAIN.getObject(questionID).disabled = true;
                        PWM_MAIN.getObject(questionID).readonly = true;
                        dataOptions.push({id:'UNSELECTED',label:'&nbsp;&mdash;&nbsp;' + initialChoiceText + '&nbsp;&mdash;&nbsp;'})
                    } else {
                        selectWidget.removeOption('UNSELECTED');
                        PWM_MAIN.getObject(questionID).disabled = false;
                        PWM_MAIN.getObject(questionID).readonly = false;
                    }
                    for (var i = 0; i < allPossibleTexts.length; i++) {
                        var loopText = allPossibleTexts[i];
                        if (loopText == selectedValue || array.indexOf(currentlySelectedTexts,loopText) == -1) {
                            dataOptions.push({id:loopText,label:loopText});
                        }
                    }
                    var store = new Memory({data:dataOptions});
                    var os = new ObjectStore({ objectStore: store });
                    selectWidget.setStore(os,selectedValue);
                }(responseID));
            }

            // add the onchange events (and store so they can be removed on next event execution
            for (var responseID in simpleRandomSelectElements) {
                (function(responseID){
                    var questionID = simpleRandomSelectElements[responseID];
                    var selectWidget = registry.byId(responseID);
                    var eventHandler = dojoOn(selectWidget,"change",function(){
                        PWM_MAIN.getObject(questionID).value = '';
                        PWM_RESPONSES.makeSelectOptionsDistinct();
                        PWM_RESPONSES.validateResponses();
                        PWM_MAIN.getObject(questionID).focus();
                    });
                    PWM_GLOBAL['randomSelectEventHandlers'].push(eventHandler);
                }(responseID));
            }
            console.log('exiting makeSelectOptionsDistinct(), duration:' + (((new Date()).getTime()) - startTime) + "ms");
        });
};

PWM_RESPONSES.startupResponsesPage=function() {
    var initialPrompt = PWM_MAIN.showString('Display_ResponsesPrompt');
    if (initialPrompt != null && initialPrompt.length > 1) {
        var messageElement = PWM_MAIN.getObject("message");
        if (messageElement.firstChild.nodeValue.length < 2) {
            PWM_MAIN.showInfo(initialPrompt);
        }
    }
};


