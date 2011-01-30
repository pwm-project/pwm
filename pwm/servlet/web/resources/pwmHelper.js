/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

var AJAX_TYPE_DELAY_MS = 100;

var PWM_STRINGS = {};
var PWM_GLOBAL = {};

function pwmPageLoadHandler() {
    for (var j = 0; j < document.forms.length; j++) {
        var loopForm = document.forms[j];
        loopForm.setAttribute('autocomplete', 'off');
    }
}

function checkForCapsLock(e) {
    var capsLockWarningElement = getObject('capslockwarning');
    if (capsLockWarningElement == null) {
        return;
    }

    var kc = e.keyCode ? e.keyCode : e.which;
    var sk = e.shiftKey ? e.shiftKey : ((kc == 16));
    if (((kc >= 65 && kc <= 90) && !sk) || ((kc >= 97 && kc <= 122) && sk)) {
        if ((e.target != null && e.target.type == 'password') || (e.srcElement != null && e.srcElement.type == 'password')) {
            capsLockWarningElement.style.visibility = 'visible';
        }
    } else {
        capsLockWarningElement.style.visibility = 'hidden';
    }
}

function handleFormSubmit(buttonID, form) {
    getObject(buttonID).value = "\u00A0\u00A0\u00A0" + PWM_STRINGS['Display_PleaseWait'] + "\u00A0\u00A0\u00A0";
    getObject(buttonID).disabled = true;

    var formElements = getObject(buttonID).form.elements;
    for (var i = 0; i < formElements.length; i++) {
        formElements[i].readOnly = true;
    }

    showWaitDialog("Please Wait....", "");

    setTimeout(function() {
        form.submit();
    }, 100);
}

function handleFormClear() {
    var focusSet = false;

    for (var j = 0; j < document.forms.length; j++) {
        for (var i = 0; i < document.forms[j].length; i++) {
            var current = document.forms[j].elements[i];
            if ((current.type == 'text') || (current.type == 'password')) {
                current.value = '';
                if (!focusSet) {
                    current.focus();
                    focusSet = true;
                }
            } else if (current.type == 'select') {
                current.selectedIndex = -1;
            }
        }
    }
}

function setFocus(elementName) {
    var object = getObject(elementName);
    object.focus();
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

// this method exists because IE doesn't support simply changing the type of object
function changeInputTypeField(object, type) {
    var newObject = document.createElement('input');
    newObject.type = type;

    if (object.size) newObject.size = object.size;
    if (object.value) newObject.value = object.value;
    if (object.name) newObject.name = object.name;
    if (object.id) newObject.id = object.id;
    if (object.className) newObject.className = object.className;
    if (object.onclick) newObject.onclick = object.onclick;
    if (object.onkeyup) newObject.onkeyup = object.onkeyup;
    if (object.onkeydown) newObject.onkeydown = object.onkeydown;
    if (object.onkeypress) newObject.onkeypress = object.onkeypress;
    if (object.disabled) newObject.disabled = object.disabled;
    if (object.readonly) newObject.readonly = object.readonly;

    object.parentNode.replaceChild(newObject, object);
    return newObject;
}

function clearDigitWidget(widgetName) {
    var oldDijitNode = dijit.byId(widgetName);
    if (oldDijitNode != null) {
        try {
            oldDijitNode.destroy();
        } catch (error) {
        }
    }
}

function startupLocaleSelectorMenu(localeData, attachNode) {
    dojo.require("dijit.Menu");
    var pMenu;
    pMenu = new dijit.Menu({
        targetNodeIds: [attachNode]
    });
    pMenu.startup();

    var loopFunction = function(pMenu, localeKey, localeDisplayName) {
        pMenu.addChild(new dijit.MenuItem({
            label: localeDisplayName,
            onClick: function() {
                var pingURL = PWM_STRINGS['url-command'] + "?processAction=idleUpdate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&pwmLocale=" + localeKey;
                dojo.xhrGet({
                    url: pingURL,
                    sync: true,
                    load: function() {
                        PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
                        window.location.reload();
                    },
                    error: function(error) {
                        alert('unable to set locale: ' + error);
                    }
                });
            }
        }));
    };

    for (var localeKey in localeData) {
        var localeDisplayName = localeData[localeKey];
        loopFunction(pMenu, localeKey, localeDisplayName);
    }
}

function showWaitDialog(title, body) {
    if (body == null || body.length < 1) {
        body = '<div style="text-align: center"><img alt="altText" src="' + PWM_STRINGS['url-resources'] + '/wait.gif"/></div>';
    }
    dojo.require("dijit.Dialog");
    var theDialog = new dijit.Dialog({
        id: 'waitDialog',
        title: title,
        style: "width: 300px",
        content: body,
        closable: false
    });
    theDialog.show();
}
