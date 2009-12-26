/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

var AJAX_TYPE_DELAY_MS = 500;

function unloadHandler() {
    /*
     var unloadPageURL = getObject("Js_CommandURL").value + "?processAction=pageUnload";
     var xmlhttp = createXmlHttpObject();
     xmlhttp.abort();
     xmlhttp.open("POST", unloadPageURL, true);
     xmlhttp.send(null);
     */
}


function checkForCapsLock(e) {
    var capsLockWarningElement = getObject('capslockwarning');
    if (capsLockWarningElement == null) {
        return;
    }

    var kc = e.keyCode?e.keyCode:e.which;
    var sk = e.shiftKey?e.shiftKey:((kc == 16));
    if(((kc >= 65 && kc <= 90) && !sk)||((kc >= 97 && kc <= 122) && sk)) {
        if (e.target.type == 'password') {
            capsLockWarningElement.style.visibility = 'visible';
        }
    } else {
        capsLockWarningElement.style.visibility = 'hidden';
    }
}

function handleFormSubmit(buttonID) {
    getObject(buttonID).value = getObject('Js_Display_PleaseWait').value;
    getObject(buttonID).disabled = true;

    var formElements = getObject(buttonID).form.elements;
    for (var i = 0; i < formElements.length; i++) {
        formElements[i].readOnly = true;
    }
}

function handleFormClear() {
    for (var j = 0; j < document.forms.length; j++) {
        for (var i = 0; i < document.forms[j].length; i++) {
            var current = document.forms[j].elements[i];
            if ((current.type == 'text') || (current.type == 'password')) {
                current.value = "";
            }
        }
    }
    return false;
}

function setFocus(elementName) {
    var object = getObject(elementName);
    object.focus();
}


function getAllFormValues() {
    var allFormsValues = "";
    for (var j = 0; j < document.forms.length; j++) {
        for (var i = 0; i < document.forms[j].length; i++) {
            var current = document.forms[j].elements[i];
            allFormsValues += i;
            allFormsValues += current.name;
            allFormsValues += current.value;
        }
    }
    return allFormsValues;
}

function createXmlHttpObject() {
    var xmlhttp = null;
    try {
        xmlhttp = new ActiveXObject("Msxml2.XMLHTTP");
    } catch (e) {
        try {
            xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
        } catch (E) {
            xmlhttp = false;
        }
    }

    if (!xmlhttp && typeof XMLHttpRequest != 'undefined') {
        xmlhttp = new XMLHttpRequest();
    }
    return xmlhttp;
}

function getObject(name) {
    var ns4 = document.layers;
    var w3c = document.getElementById;
    var ie4 = document.all;

    if (ns4) {
        return eval('document.' + name);
    }
    if (w3c) {
        return document.getElementById(name);
    }
    if (ie4) {
        return eval('document.all.' + name);
    }
    return false;
}

function trimString(sInString) {
    sInString = sInString.replace(/^\s+/g, "");
    // strip leading
    return sInString.replace(/\s+$/g, "");
    // strip trailing
}

function closeWindow() {
    window.close();
}

function urlDecode(str) {
    str = str.replace(new RegExp('\\+', 'g'), ' ');
    return unescape(str);
}
function urlEncode(str) {
    str = escape(str);
    str = str.replace(new RegExp('\\+', 'g'), '%2B');
    return str.replace(new RegExp('%20', 'g'), '+');
}

// this method exists because IE doesn't support simply changing the type of object
function changeInputTypeField(object,type){
    var newObject = document.createElement('input');
    newObject.type = type;

    if(object.size) newObject.size = object.size;
    if(object.value) newObject.value = object.value;
    if(object.name) newObject.name = object.name;
    if(object.id) newObject.id = object.id;
    if(object.className) newObject.className = object.className;
    if(object.onclick) newObject.onclick = object.onclick;
    if(object.onkeyup) newObject.onkeyup = object.onkeyup;
    if(object.onkeydown) newObject.onkeydown = object.onkeydown;
    if(object.onkeypress) newObject.onkeypress = object.onkeypress;
    if(object.disabled) newObject.disabled = object.disabled;
    if(object.readonly) newObject.readonly = object.readonly;

    object.parentNode.replaceChild(newObject,object);
    return newObject;
}

function AjaxRequestorState(requestURL, callbackFunction, createKeyFunction) {
    this.SETTING_TIMEOUT = 10 * 1000;            // max time to wait for a password check (ms)
    this.STATIC_COMMUNICATION_ERROR = "---COMMUNICATION_ERROR";

    this.requestor = createXmlHttpObject();

    this.counter = 0;
    this.cache = new Array();
    this.busy = false;

    this.callBackFunction = callbackFunction;
    this.createKeyFunction = createKeyFunction;
    this.requestURL = requestURL;
}

function doAjaxRequest(requestorState, key)
{
    var cachedResponse = requestorState.cache[key];
    if (cachedResponse != null) {
        requestorState.callBackFunction(key,cachedResponse);
    } else {
        if (requestorState.busy || requestorState.requestor == null) {
            return;
        }

        requestorState.busy = true;

        setTimeout(function() {processAjaxRequest(requestorState, key);}, AJAX_TYPE_DELAY_MS);
    }
}

function processAjaxRequest(requestorState, key)
{
    if (requestorState.createKeyFunction != null) {
        var currentKey = requestorState.createKeyFunction();
        if (currentKey != key) {
            setTimeout(function() {processAjaxRequest(requestorState, currentKey);}, AJAX_TYPE_DELAY_MS);
            return;
        }
    }

    if (requestorState.requestor.readyState != 0) {
        requestorState.requestor.abort();
    }

    requestorState.requestor.open("POST", requestorState.requestURL, true);
    requestorState.requestor.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    requestorState.requestor.setRequestHeader("Content-length", key.length);

    requestorState.requestor.onreadystatechange = function() {
        if (requestorState.requestor.readyState == 4) {
            var response = requestorState.requestor.responseText;
            requestorState.busy = false;
            requestorState.retryCounter = 0;
            requestorState.counter = requestorState.counter + 1;
            requestorState.callBackFunction(key, response);
        }
    };

    try {
        requestorState.requestor.send(key);

        var thisReqCounterValue = requestorState.counter;
        setTimeout(function() {
            if (thisReqCounterValue == requestorState.counter) {
                requestorState.busy = false;
                requestorState.abort();
                requestorState.callBackFunction(key,  requestorState.STATIC_COMMUNICATION_ERROR);
            }
        },requestorState.SETTING_TIMEOUT);
    } catch (e) {
        requestorState.busy = false;
        requestorState.cache = new Array();
        requestorState.callBackFunction(key,  requestorState.STATIC_COMMUNICATION_ERROR);
    }
}