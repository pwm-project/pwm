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

function pwmPageLoadHandler() {
    for (var j = 0; j < document.forms.length; j++) {
        var loopForm = document.forms[j];
        loopForm.setAttribute('autocomplete', 'off');
    }

    setTimeout(function() {
        dojo.require("dijit.Dialog");
    },10 * 1000);
}

function checkForCapsLock(e) {
    var capsLockWarningElement = getObject('capslockwarning');
    if (capsLockWarningElement == null) {
        return;
    }

    var capsLockKeyDetected = false;
    var elementTarget = null;
    if (e.target != null) {
        elementTarget = e.target;
    } else if (e.srcElement != null) {
        elementTarget = e.srcElement;
    }
    if (elementTarget != null) {
        if (elementTarget.nodeName == 'input' || elementTarget.nodeName == 'INPUT') {
            var kc = e.keyCode ? e.keyCode : e.which;
            var sk = e.shiftKey ? e.shiftKey : ((kc == 16));
            if (((kc >= 65 && kc <= 90) && !sk) || ((kc >= 97 && kc <= 122) && sk)) {
                capsLockKeyDetected = true;
            }
        }
    }

    var displayDuration = 5 * 1000;
    var fadeOutArgs = { node: "capslockwarning", duration: 3 * 1000 };
    var fadeInArgs = { node: "capslockwarning", duration: 200 };
    if (capsLockKeyDetected) {
        capsLockWarningElement.style.display = null;
        dojo.fadeIn(fadeInArgs).play();
        PWM_GLOBAL['lastCapsLockErrorTime'] = (new Date().getTime());
        setTimeout(function(){
            if ((new Date().getTime() - PWM_GLOBAL['lastCapsLockErrorTime'] > displayDuration)) {
                dojo.fadeOut(fadeOutArgs).play();
                setTimeout(function(){
                    if ((new Date().getTime() - PWM_GLOBAL['lastCapsLockErrorTime'] > displayDuration)) {
                        capsLockWarningElement.style.display = 'none';
                    }
                },5 * 1000);
            }
        },displayDuration + 500);
    } else {
        dojo.fadeOut(fadeOutArgs).play();
    }
}

function handleFormSubmit(buttonID, form) {
    PWM_GLOBAL['idle_suspendTimeout'] = true;
    var submitButton = getObject(buttonID);
    if (submitButton != null) {
        getObject(buttonID).value = PWM_STRINGS['Display_PleaseWait'];
        getObject(buttonID).disabled = true;

        var formElements = submitButton.form.elements;
        for (var i = 0; i < formElements.length; i++) {
            formElements[i].readOnly = true;
        }
    }

    showWaitDialog(PWM_STRINGS['Display_PleaseWait'], "");

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

function startupLocaleSelectorMenu(attachNode) {
    var localeData = PWM_GLOBAL['localeInfo'];

    if (getObject(attachNode) == null) {
        return;
    }

    dojo.require("dijit.Menu");
    var pMenu = new dijit.Menu({
        targetNodeIds: [attachNode],
        leftClickToOpen: true
    });
    pMenu.startup();

    dojo.require("dijit.MenuItem");
    var loopFunction = function(pMenu, localeKey, localeDisplayName, localeIconClass) {
        pMenu.addChild(new dijit.MenuItem({
            label: localeDisplayName,
            iconClass: localeIconClass,
            onClick: function() {
                var pingURL = PWM_GLOBAL['url-command'] + "?processAction=idleUpdate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&pwmLocale=" + localeKey;
                dojo.xhrGet({
                    url: pingURL,
                    sync: true,
                    preventCache: true,
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
        var loopDisplayName = localeKey == '' ? 'English' : localeData[localeKey];
        var loopIconClass = "flagLang_" + (localeKey == '' ? 'en' : localeKey);
        var loopKey = localeKey == '' ? 'default' : localeKey;
        loopFunction(pMenu, loopKey, loopDisplayName, loopIconClass);
    }
}

function showWaitDialog(title, body) {
    var idName = 'waitDialogID';
    clearDigitWidget(idName);
    if (body == null || body.length < 1) {
        body = '<div id="WaitDialogBlank"/>';
    }
    dojo.require("dijit.Dialog");
    var theDialog = new dijit.Dialog({
        id: idName,
        title: title,
        style: "width: 300px",
        content: body,
        closable: false

    });
    theDialog.show();
}

function showPwmHealth(parentDivID, refreshNow) {
    var parentDiv = dojo.byId(parentDivID);
    PWM_GLOBAL['healthCheckInProgress'] = "true";

    setTimeout(function() {
        if (PWM_GLOBAL['healthCheckInProgress']) {
            parentDiv.innerHTML = '<div id="WaitDialogBlank"/>';
        }
    }, 1000);

    var refreshUrl = PWM_GLOBAL['url-restservice'] + "/pwm-health";
    if (refreshNow) {
        refreshUrl += "?refreshImmediate=true&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    } else {
        refreshUrl += "?pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    }

    dojo.xhrGet({
        url: refreshUrl,
        handleAs: "json",
        headers: { "Accept": "application/json" },
        timeout: 60 * 1000,
        preventCache: true,
        load: function(data) {
            PWM_GLOBAL['pwm-health'] = data['overall'];
            var healthRecords = data['data'];
            var htmlBody = '<table width="100%" style="width=100%; border=0">';
            for (var i = 0; i < healthRecords.length; i++) {
                var healthData = healthRecords[i];
                htmlBody += '<tr><td class="key" style="width:1px; white-space:nowrap;"">';
                htmlBody += healthData['topic'];
                htmlBody += '</td><td class="health-' + healthData['status'] + '">';
                htmlBody += healthData['status'];
                htmlBody += "</td><td>";
                htmlBody += healthData['detail'];
                htmlBody += "</td></tr>";
            }

            htmlBody += '<tr><td colspan="3" style="text-align:center;">';
            htmlBody += new Date(data['timestamp']).toLocaleString() + '&nbsp;&nbsp;&nbsp;&nbsp;';
            htmlBody += '<a href="#"; onclick="showPwmHealth(\'' + parentDivID + '\',true)">refresh</a>';
            htmlBody += "</td></tr>";

            htmlBody += '</table>';
            parentDiv.innerHTML = htmlBody;
            PWM_GLOBAL['healthCheckInProgress'] = false;
            setTimeout(function() {
                showPwmHealth(parentDivID, false);
            }, 10 * 1000);
        },
        error: function(error) {
            var htmlBody = '<div style="text-align:center; background-color: #d20734">';
            htmlBody += '<br/><span style="font-weight: bold;">unable to load health data</span></br>';

            htmlBody += '<br/>' + error + '<br/>';
            htmlBody += '<br/>' + new Date().toLocaleString() + '&nbsp;&nbsp;&nbsp;';
            htmlBody += '<a href=""; onclick="showPwmHealth(\'' + parentDivID + '\',false)">retry</a><br/><br/>';
            htmlBody += '</div>';
            parentDiv.innerHTML = htmlBody;
            PWM_GLOBAL['healthCheckInProgress'] = false;
            PWM_GLOBAL['pwm-health'] = 'UNKNOWN';
            setTimeout(function() {
                showPwmHealth(parentDivID, false);
            }, 10 * 1000);
        }
    });
}


// -- idle timeout handler  --
var SETTING_LOOP_FREQUENCY = 1000;
var SETTING_PING_FREQUENCY = 10;
var SETTING_WARN_SECONDS = 30;

function initCountDownTimer(secondsRemaining) {
    PWM_GLOBAL['idle_Timeout'] = secondsRemaining;
    PWM_GLOBAL['idle_dateFuture'] = new Date(new Date().getTime() + (secondsRemaining * 1000));
    PWM_GLOBAL['idle_lastPingTime'] = new Date().getTime();
    PWM_GLOBAL['real-window-title'] = document.title;
    resetIdleCounter();
    setInterval("pollActivity()", SETTING_LOOP_FREQUENCY); //poll scrolling
    document.onclick = resetIdleCounter;
    document.onkeydown = resetIdleCounter;
}

function resetIdleCounter() {
    var idleSeconds = calcIdleSeconds();
    closeIdleWarning();
    getObject("idle_status").firstChild.nodeValue = makeIdleDisplayString(idleSeconds);

    PWM_GLOBAL['idle_dateFuture'] = new Date(new Date().getTime() + (PWM_GLOBAL['idle_Timeout'] * 1000));
    {
        var dateNow = new Date().getTime();
        var amount = dateNow - PWM_GLOBAL['idle_lastPingTime'];

        if (amount > SETTING_PING_FREQUENCY * 1000) { //calc milliseconds between dates
            pingServer();
            PWM_GLOBAL['idle_lastPingTime'] = dateNow;
            PWM_GLOBAL['idle_sendPing'] = false;
        }
    }

    PWM_GLOBAL['idle_warningDisplayed'] = false;
}

function pollActivity() {
    var idleSeconds = calcIdleSeconds();
    var idleDisplayString = makeIdleDisplayString(idleSeconds);
    var idleStatusFooter = getObject("idle_status");
    if (idleStatusFooter != null) {
        idleStatusFooter.firstChild.nodeValue = idleDisplayString;
    }

    var warningDialogText = getObject("IdleDialogWindowIdleText");
    if (warningDialogText != null) {
        warningDialogText.firstChild.nodeValue = idleDisplayString;
    }

    if (idleSeconds < 0) {
        if (!PWM_GLOBAL['idle_suspendTimeout']) {
            PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
            PWM_GLOBAL['idle_suspendTimeout'] = true;
            window.location = PWM_GLOBAL['url-logout'];
        }
    }

    if (idleSeconds < SETTING_WARN_SECONDS) {
        showIdleWarning();
        if (idleSeconds % 2 == 0) {
            document.title = PWM_GLOBAL['real-window-title'];
        } else {
            document.title = idleDisplayString;
        }
    }
}

function pingServer() {
    var pingURL = PWM_GLOBAL['url-command'] + "?processAction=idleUpdate&time=" + new Date().getTime() + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    dojo.xhrPost({
        url: pingURL,
        sync: false
    });
}

function calcIdleSeconds() {
    var amount = PWM_GLOBAL['idle_dateFuture'].getTime() - (new Date()).getTime(); //calc milliseconds between dates
    amount = Math.floor(amount / 1000); //kill the "milliseconds" so just secs
    return amount;
}

function makeIdleDisplayString(amount) {
    if (amount < 1) {
        return "";
    }

    var output = "";
    var days = 0, hours = 0, mins = 0, secs = 0;

    days = Math.floor(amount / 86400);

    amount = amount % 86400;
    hours = Math.floor(amount / 3600);

    amount = amount % 3600;
    mins = Math.floor(amount / 60);

    amount = amount % 60;
    secs = Math.floor(amount);

    // write number of days
    if (days != 0) {
        output += days + " ";
        if (days != 1) {
            output += PWM_STRINGS['Display_Days'];
        } else {
            output += PWM_STRINGS['Display_Day'];
        }
    }

    // write number of hours
    if (days != 0 || hours != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += hours + " ";
        if (hours != 1) {
            output += PWM_STRINGS['Display_Hours'];
        } else {
            output += PWM_STRINGS['Display_Hour'];
        }
    }

    // write number of minutes
    if (days != 0 || hours != 0 || mins != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += mins + " ";
        if (mins != 1) {
            output += PWM_STRINGS['Display_Minutes'];
        } else {
            output += PWM_STRINGS['Display_Minute'];
        }
    }

    // write number of seconds
    if (mins < 4) {
        if (output.length > 0) {
            output += ", ";
        }

        output += secs + " ";

        if (secs != 1) {
            output += PWM_STRINGS['Display_Seconds'];
        } else {
            output += PWM_STRINGS['Display_Second'];
        }
    }

    output = PWM_STRINGS['Display_IdleTimeout'] + " " + output;
    return output;
}

function showIdleWarning() {
    if (!PWM_GLOBAL['idle_warningDisplayed']) {
        PWM_GLOBAL['idle_warningDisplayed'] = true;

        var dialogBody = PWM_STRINGS['Display_IdleWarningMessage'] + '<br/><br/><span id="IdleDialogWindowIdleText">&nbsp;</span>';

        dojo.require("dijit.Dialog");
        var theDialog = new dijit.Dialog({
            title: PWM_STRINGS['Display_IdleWarningTitle'],
            style: "width: 260px; border: 2px solid #D4D4D4;",
            content: dialogBody,
            closable: false,
            draggable: false,
            id: "idleDialog"
        });
        theDialog.setAttribute('class', 'tundra');
        theDialog.show();
    }
}

function closeIdleWarning() {
    var dialog = dijit.byId('idleDialog');
    if (dialog != null) {
        dialog.hide();
        dialog.destroyRecursive();
    }
    document.title = PWM_GLOBAL['real-window-title'];
}

function clearError()
{
    var errorObject = getObject("message");
    if (errorObject == null) {
        return;
    }
    errorObject.firstChild.nodeValue = '\u00a0';

    var destStyle = errorObject.parentNode.style;
    var destBackground = destStyle.backgroundColor;

    dojo.animateProperty({
        node:"message",
        duration: 500,
        properties: {
            backgroundColor: destBackground
        }
    }).play();
}

function showInfo(infoMsg)
{
    doShow('message-info',infoMsg)
}

function showError(errorMsg)
{
    doShow('message-error',errorMsg);
}

function showSuccess(successMsg)
{
    doShow('message-success',successMsg);
}

function doShow(destClass, message) {
    var messageElement = getObject("message");
    if (messageElement == null || messageElement.firstChild == null || messageElement.firstChild.nodeValue == null) {
        return;
    }
    messageElement.firstChild.nodeValue = message;

    if(dojo.isIE <= 8){ // only IE7 and below
        messageElement.className = "message " + destClass;
    } else {
        try {
            // create a temp element and place it on the page to figure out what the destination color should be
            var tempDivElement = document.createElement('div');
            tempDivElement.className = "message " + destClass;
            tempDivElement.style.visibility = "hidden";
            tempDivElement.id = "tempDivElement";
            messageElement.appendChild(tempDivElement);
            var destStyle = window.getComputedStyle(tempDivElement, null);
            var destBackground = destStyle.backgroundColor;
            var destColor = destStyle.color;

            dojo.animateProperty({
                node:"message",
                duration: 500,
                properties: {
                    backgroundColor: destBackground,
                    color: destColor
                }
            }).play();

            dojo.query('#tempDivElement').orphan();
        } catch (e) {
            messageElement.className = "message " + destClass;
        }
    }
}
