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

var PWM_RESPONSES = PWM_RESPONSES || {};
var PWM_VAR = PWM_VAR || {};
var require;

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
            return domForm.toObject('form-setupResponses');
        };
        validationProps['processResultsFunction'] = function(data){
            PWM_RESPONSES.updateDisplay(data['data']);
        };

        PWM_MAIN.pwmFormValidator(validationProps);
    });
};

PWM_RESPONSES.updateDisplay=function(resultInfo) {
    if (resultInfo == null) {
        PWM_MAIN.getObject("button-setResponses").disabled = false;
        return;
    }

    var result = resultInfo["message"];

    if (resultInfo["success"] == true) {
        PWM_MAIN.getObject("button-setResponses").disabled = false;
        PWM_MAIN.showSuccess(result);
    } else {
        PWM_MAIN.getObject("button-setResponses").disabled = true;
        PWM_MAIN.showError(result);
    }
};

PWM_RESPONSES.makeSelectOptionsDistinct=function() {
    var startTime = (new Date()).getTime();
    console.log('entering makeSelectOptionsDistinct()');

    // all possible random questions (populated by the jsp)
    var allPossibleTexts = PWM_VAR['simpleRandomOptions'];

    // string that is used at the top of unconfigured select list
    var initialChoiceText = PWM_MAIN.showString('Display_SelectionIndicator');

    // the HTML select elements (populated by the jsp)
    var simpleRandomSelectElements = PWM_VAR['simpleRandomSelectElements'];

    // texts that are in use
    var currentlySelectedTexts = [];

    for (var loopID in simpleRandomSelectElements) {
        (function(iterID){
            var questionID = simpleRandomSelectElements[iterID];
            var selectedElement = PWM_MAIN.getObject(questionID);
            var selectedIndex = selectedElement.selectedIndex;
            var selectedValue = selectedElement.options[selectedIndex].value;
            if ('UNSELECTED' != selectedValue) {
                currentlySelectedTexts.push(selectedValue);
            }
        }(loopID));
    }

    // repopulate the select elements
    for (var loopID in simpleRandomSelectElements) {
        (function(iterID){
            var questionID = simpleRandomSelectElements[iterID];
            var selectedElement = PWM_MAIN.getObject(questionID);
            var selectedIndex = selectedElement.selectedIndex;
            var selectedValue = selectedElement.options[selectedIndex].value;
            var responseID = selectedElement.getAttribute('data-response-id');
            selectedElement.innerHTML = '';
            if (selectedValue == 'UNSELECTED') {
                PWM_MAIN.getObject(responseID).disabled = true;
                PWM_MAIN.getObject(responseID).readonly = true;
                var unselectedOption = document.createElement('option');
                unselectedOption.value = 'UNSELECTED';
                unselectedOption.innerHTML = '&nbsp;&mdash;&nbsp;' + initialChoiceText + '&nbsp;&mdash;&nbsp;';
                unselectedOption.selected = true;
                selectedElement.appendChild(unselectedOption);
            } else {
                PWM_MAIN.getObject(responseID).disabled = false;
                PWM_MAIN.getObject(responseID).readonly = false;
            }

            for (var i = 0; i < allPossibleTexts.length; i++) {
                var loopText = allPossibleTexts[i];
                var optionElement = document.createElement('option');
                optionElement.value = loopText;
                optionElement.innerHTML = loopText;

                require(["dojo/_base/array"], function(array){
                    if (loopText == selectedValue || array.indexOf(currentlySelectedTexts,loopText) == -1) {
                        if (loopText == selectedValue) {
                            optionElement.selected = true;
                        }
                        selectedElement.appendChild(optionElement);
                    }
                });
            }
        }(loopID));
    }

    console.log('exiting makeSelectOptionsDistinct(), duration:' + (((new Date()).getTime()) - startTime) + "ms");
};

PWM_RESPONSES.startupResponsesPage=function() {
    var initialPrompt = PWM_MAIN.showString('Display_ResponsesPrompt');
    if (initialPrompt != null && initialPrompt.length > 1) {
        var messageElement = PWM_MAIN.getObject("message");
        if (messageElement.firstChild.nodeValue.length < 2) {
            PWM_MAIN.showInfo(initialPrompt);
        }
    }
    PWM_MAIN.addEventHandler('form-setupResponses','input',function(){
        console.log('form-setupResponses input event handler');
        PWM_RESPONSES.validateResponses();
    });
    PWM_MAIN.getObject("button-setResponses").disabled = true;
    PWM_RESPONSES.initSimpleRandomElements();
};


PWM_RESPONSES.initSimpleRandomElements = function() {
    PWM_VAR['simpleRandomSelectElements'] = [];
    PWM_VAR['focusInValues'] = {};
    require(["dojo/query","dojo/on"], function(query,on){
        var results = query('.simpleModeResponseSelection');
        for (var i = 0; i < results.length; i++) {
            (function(itemIterator){
                var element = results[itemIterator];
                PWM_VAR['simpleRandomSelectElements'].push(element.id);
                on(element, "focusin", function(){
                    PWM_VAR['focusInValues'][element.id] = element.selectedIndex;
                });
                on(element, "click,blur", function(){
                    if (PWM_VAR['focusInValues'][element.id] != element.selectedIndex) {
                        var selectedIndex = element.selectedIndex;
                        var selectedValue = element.options[selectedIndex].value;
                        if (selectedValue != 'UNSELECTED') {
                            PWM_RESPONSES.makeSelectOptionsDistinct();
                            var responseID = element.getAttribute('data-response-id');
                            PWM_MAIN.getObject(responseID).value = '';
                            PWM_MAIN.getObject(responseID).disabled = false;
                            PWM_MAIN.getObject(responseID).focus();
                        }
                    }
                });
            })(i);
        }
    });
};
