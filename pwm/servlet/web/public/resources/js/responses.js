/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

var responsesHidden = true;
var PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
var PARAM_QUESTION_PREFIX = "PwmResponse_Q_";

PWM_GLOBAL['simpleRandomSelectElements'] = {};
PWM_GLOBAL['simpleRandomOptions'] = [];

// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateResponses() {
    require(["dojo/dom-form"], function(domForm){
        var serviceUrl = PWM_GLOBAL['url-setupresponses'] + "?processAction=validateResponses";
        if (PWM_GLOBAL['responseMode']) {
            serviceUrl += "&responseMode=" + PWM_GLOBAL['responseMode'];
        }
        var validationProps = {};
        validationProps['messageWorking'] = showString('Display_CheckingResponses');
        validationProps['serviceURL'] = serviceUrl;
        validationProps['readDataFunction'] = function(){
            return domForm.toObject('setupResponses');
        };
        validationProps['processResultsFunction'] = function(data){
            updateDisplay(data);
        };

        pwmFormValidator(validationProps);
    });
}

function updateDisplay(resultInfo)
{
    if (resultInfo == null) {
        getObject("setresponses_button").disabled = false;
        return;
    }

    var result = resultInfo["message"];

    if (resultInfo["success"] == true) {
        getObject("setresponses_button").disabled = false;
        showSuccess(result);
    } else {
        getObject("setresponses_button").disabled = true;
        showError(result);
    }
}

function makeSelectOptionsDistinct() {
    require(["dojo","dijit/registry","dojo/_base/array","dojo/on","dojo/data/ObjectStore","dojo/store/Memory"],
        function(dojo,registry,array,dojoOn,ObjectStore,Memory){
        console.log('entering makeSelectOptionsDistinct()');
        var allPossibleTexts = PWM_GLOBAL['simpleRandomOptions'];
        var initialChoiceText = showString('Display_SelectionIndicator');
        var simpleRandomSelectElements = PWM_GLOBAL['simpleRandomSelectElements'];
        var currentlySelectedTexts = [];

        for (var responseID in simpleRandomSelectElements) {
            (function(responseID){
                var selectWidget = registry.byId(responseID);
                var selectedValue = selectWidget.get('value');
                currentlySelectedTexts.push(selectedValue);
            }(responseID));
        }

        for (var responseID in simpleRandomSelectElements) {
            (function(responseID){
                var questionID = simpleRandomSelectElements[responseID];
                var selectWidget = registry.byId(responseID);
                selectWidget.set('onchange',null);
                var selectedValue = selectWidget.get('value');
                var dataOptions = [];
                if (selectedValue == 'UNSELECTED') {
                    getObject(questionID).disabled = true;
                    getObject(questionID).readonly = true;
                    dataOptions.push({id:'UNSELECTED',label:'&nbsp;&nbsp---' + initialChoiceText + '---'})
                } else {
                    selectWidget.removeOption('UNSELECTED');
                    getObject(questionID).disabled = false;
                    getObject(questionID).readonly = false;
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
                dojoOn.once(selectWidget,"change",function(){
                    getObject(questionID).value = '';
                    makeSelectOptionsDistinct();
                    validateResponses();
                });
            }(responseID));
        }
    });
}

function startupResponsesPage()
{
    var initialPrompt = showString('Display_ResponsesPrompt');
    if (initialPrompt != null && initialPrompt.length > 1) {
        var messageElement = getObject("message");
        if (messageElement.firstChild.nodeValue.length < 2) {
            showInfo(initialPrompt);
        }
    }
}


