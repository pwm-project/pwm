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

    require(["dijit/Dialog"],function(){
        showWaitDialog();

        setTimeout(function() {
            form.submit();
        }, 300);
    });
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

function clearDijitWidget(widgetName) {
    require(["dijit/registry"],function(dijit){
        var oldDijitNode = dijit.byId(widgetName);
        if (oldDijitNode != null) {

            try {
                oldDijitNode.destroyRecursive();
            } catch (error) {
            }

            try {
                oldDijitNode.destroy();
            } catch (error) {
            }
        }
    });
}

function initLocaleSelectorMenu(attachNode) {
    var localeData = PWM_GLOBAL['localeInfo'];

    if (getObject(attachNode) == null) {
        return;
    }

    require(["dojo/domReady!","dijit/Menu","dijit/MenuItem","dijit/Dialog"],function(){
        var pMenu = new dijit.Menu({
            targetNodeIds: [attachNode],
            leftClickToOpen: true
        });
        pMenu.startup();

        var loopFunction = function(pMenu, localeKey, localeDisplayName, localeIconClass) {
            pMenu.addChild(new dijit.MenuItem({
                label: localeDisplayName,
                iconClass: localeIconClass,
                onClick: function() {
                    showWaitDialog();
                    var pingURL = PWM_GLOBAL['url-command'] + "?processAction=idleUpdate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&pwmLocale=" + localeKey;
                    dojo.xhrGet({
                        url: pingURL,
                        sync: false,
                        preventCache: true,
                        load: function() {
                            PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
                            setTimeout(function(){window.location.reload();},1000);
                        },
                        error: function(error) {
                            alert('unable to set locale: ' + error);
                        }
                    });
                }
            }));
        };

        for (var localeKey in localeData) {
            var loopDisplayName = localeData[localeKey];
            var loopIconClass = "flagLang_" + (localeKey == '' ? 'en' : localeKey);
            var loopKey = localeKey == '' ? 'default' : localeKey;
            loopFunction(pMenu, loopKey, loopDisplayName, loopIconClass);
        }
    });
}

function showWaitDialog(title, body) {
    if (title == null) {
        title=PWM_STRINGS['Display_PleaseWait'];
    }
    require(["dojo","dijit/Dialog"],function(dojo){
        var idName = 'dialogPopup';
        clearDijitWidget(idName);
        if (body == null || body.length < 1) {
            body = '<div id="WaitDialogBlank"/>';
        }
        var theDialog = new dijit.Dialog({
            id: idName,
            title: title,
            style: "width: 300px",
            content: body
        });
        dojo.style(theDialog.closeButtonNode,"display","none");
        theDialog.show();
    });
}

function showPwmHealth(parentDivID, refreshNow, showRefresh) {
    require(["dojo"],function(dojo){

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
                if (showRefresh) {
                    htmlBody += '<a href="#"; onclick="showPwmHealth(\'' + parentDivID + '\',true,true)">refresh</a>';
                }
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
                if (error != null) {
                    htmlBody += '<br/>' + error + '<br/>';
                }
                htmlBody += '<br/>' + new Date().toLocaleString() + '&nbsp;&nbsp;&nbsp;';
                if (showRefresh) {
                    htmlBody += '<a href="#" onclick="showPwmHealth(\'' + parentDivID + '\',false,true)">retry</a><br/><br/>';
                }
                htmlBody += '</div>';
                parentDiv.innerHTML = htmlBody;
                PWM_GLOBAL['healthCheckInProgress'] = false;
                PWM_GLOBAL['pwm-health'] = 'WARN';
                setTimeout(function() {
                    showPwmHealth(parentDivID, false);
                }, 10 * 1000);
            }
        });
    });
}

var IdleTimeoutHandler = {};

IdleTimeoutHandler.SETTING_LOOP_FREQUENCY = 1000; // milliseconds
IdleTimeoutHandler.SETTING_PING_FREQUENCY = 10;   // seconds
IdleTimeoutHandler.SETTING_WARN_SECONDS = 30;     // seconds

IdleTimeoutHandler.initCountDownTimer = function(secondsRemaining) {
    PWM_GLOBAL['idle_Timeout'] = secondsRemaining;
    PWM_GLOBAL['idle_dateFuture'] = new Date(new Date().getTime() + (secondsRemaining * 1000));
    PWM_GLOBAL['idle_lastPingTime'] = new Date().getTime();
    PWM_GLOBAL['real-window-title'] = document.title;
    IdleTimeoutHandler.resetIdleCounter();
    setInterval("IdleTimeoutHandler.pollActivity()", IdleTimeoutHandler.SETTING_LOOP_FREQUENCY); //poll scrolling
    document.onclick = IdleTimeoutHandler.resetIdleCounter;
    document.onkeydown = IdleTimeoutHandler.resetIdleCounter;
};

IdleTimeoutHandler.resetIdleCounter = function() {
    var idleSeconds = IdleTimeoutHandler.calcIdleSeconds();
    IdleTimeoutHandler.closeIdleWarning();
    getObject("idle_status").firstChild.nodeValue = IdleTimeoutHandler.makeIdleDisplayString(idleSeconds);

    PWM_GLOBAL['idle_dateFuture'] = new Date(new Date().getTime() + (PWM_GLOBAL['idle_Timeout'] * 1000));
    {
        var dateNow = new Date().getTime();
        var amount = dateNow - PWM_GLOBAL['idle_lastPingTime'];

        if (amount > IdleTimeoutHandler.SETTING_PING_FREQUENCY * 1000) { //calc milliseconds between dates
            IdleTimeoutHandler.            pingServer();
            PWM_GLOBAL['idle_lastPingTime'] = dateNow;
            PWM_GLOBAL['idle_sendPing'] = false;
        }
    }

    PWM_GLOBAL['idle_warningDisplayed'] = false;
};

IdleTimeoutHandler.pollActivity = function() {
    var idleSeconds = IdleTimeoutHandler.calcIdleSeconds();
    var idleDisplayString = IdleTimeoutHandler.makeIdleDisplayString(idleSeconds);
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

    if (idleSeconds < IdleTimeoutHandler.SETTING_WARN_SECONDS) {
        IdleTimeoutHandler.showIdleWarning();
        if (idleSeconds % 2 == 0) {
            document.title = PWM_GLOBAL['real-window-title'];
        } else {
            document.title = idleDisplayString;
        }
    }
};

IdleTimeoutHandler.pingServer = function() {
    var pingURL = PWM_GLOBAL['url-command'] + "?processAction=idleUpdate&time=" + new Date().getTime() + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    dojo.xhrPost({
        url: pingURL,
        sync: false
    });
};

IdleTimeoutHandler.calcIdleSeconds = function() {
    var amount = PWM_GLOBAL['idle_dateFuture'].getTime() - (new Date()).getTime(); //calc milliseconds between dates
    amount = Math.floor(amount / 1000); //kill the "milliseconds" so just secs
    return amount;
};

IdleTimeoutHandler.makeIdleDisplayString = function(amount) {
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
};

IdleTimeoutHandler.showIdleWarning = function() {
    if (!PWM_GLOBAL['idle_warningDisplayed']) {
        PWM_GLOBAL['idle_warningDisplayed'] = true;

        var dialogBody = PWM_STRINGS['Display_IdleWarningMessage'] + '<br/><br/><span id="IdleDialogWindowIdleText">&nbsp;</span>';
        require(["dijit/Dialog"],function(){
            var theDialog = new dijit.Dialog({
                title: PWM_STRINGS['Display_IdleWarningTitle'],
                style: "width: 260px; border: 2px solid #D4D4D4;",
                content: dialogBody,
                closable: true,
                draggable: false,
                id: "idleDialog"
            });
            theDialog.show();
        });
    }
};

IdleTimeoutHandler.closeIdleWarning = function() {
    clearDijitWidget('idleDialog');
    document.title = PWM_GLOBAL['real-window-title'];
};

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

    try {
        messageElement.style.display = 'inherit'; // doesn't work in older ie browsers
    } catch (e) {
        messageElement.style.display = 'block';
    }

    require(["dojo"],function(dojo){
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
    });
}

function showStatChart(statName,days,divName) {
    var epsTypes = ["PASSWORD_CHANGES_10","PASSWORD_CHANGES_60","PASSWORD_CHANGES_240","AUTHENTICATION_10","AUTHENTICATION_60","AUTHENTICATION_240"];
    require(["dojo",
        "dijit",
        "dijit/registry",
        "dojox/charting/Chart2D",
        "dojox/charting/axis2d/Default",
        "dojox/charting/plot2d/Default",
        "dojox/charting/themes/Wetland",
        "dijit/form/Button",
        "dojox/gauges/GlossyCircularGauge",
        "dojo/domReady!"],
        function(dojo,dijit){
            var statsGetUrl = PWM_GLOBAL['url-restservice'] + "/statistics";
            statsGetUrl += "?pwmFormID=" + PWM_GLOBAL['pwmFormID'];
            statsGetUrl += "&statName=" + statName;
            statsGetUrl += "&days=" + days;

            dojo.xhrGet({
                url: statsGetUrl,
                handleAs: "json",
                headers: { "Accept": "application/json" },
                timeout: 15 * 1000,
                preventCache: true,
                error: function(data) {
                    for (var loopEpsIndex = 0; loopEpsIndex < epsTypes.length; loopEpsIndex++) { // clear all the gauges
                        var loopEpsName = epsTypes[loopEpsIndex] + '';
                        var loopEpsID = "EPS-GAUGE-" + loopEpsName;
                        if (getObject(loopEpsID) != null) {
                            if (dijit.byId(loopEpsID)) {
                                dijit.byId(loopEpsID).setAttribute('value',0);
                            }
                        }
                    }
                },
                load: function(data) {
                    {// gauges
                        for (var loopEpsIndex = 0; loopEpsIndex < epsTypes.length; loopEpsIndex++) {
                            var loopEpsName = epsTypes[loopEpsIndex] + '';
                            var loopEpsID = "EPS-GAUGE-" + loopEpsName;
                            var loopEpsValue = (data['EPS'])[loopEpsName] * 60;
                            var loopTop = (data['EPS'])[loopEpsName.substring(0,4) == 'AUTH' ? 'AUTHENTICATION_TOP' : "PASSWORD_CHANGES_TOP"];
                            if (getObject(loopEpsID) != null) {
                                console.log('loopEps=' + loopEpsName);
                                if (dijit.byId(loopEpsID)) {
                                    dijit.byId(loopEpsID).setAttribute('value',loopEpsValue);
                                    dijit.byId(loopEpsID).setAttribute('max',loopTop);
                                } else {
                                    var glossyCircular = new dojox.gauges.GlossyCircularGauge({
                                        background: [255, 255, 255, 0],
                                        noChange: true,
                                        value: loopEpsValue,
                                        max: loopTop,
                                        needleColor: 'yellow',
                                        //majorTicksInterval: 200,
                                        //minorTicksInterval: 50,
                                        id: loopEpsID,
                                        width: 200,
                                        height: 150
                                    }, dojo.byId(loopEpsID));
                                    glossyCircular.startup();
                                }
                            }
                        }
                    }
                    if (divName != null && getObject(divName)) { // stats chart
                        var values = [];
                        for(var key in data) {
                            var value = data[key];
                            values.push(parseInt(value));
                        }

                        if (PWM_GLOBAL[divName + '-stored-reference']) {
                            var existingChart = PWM_GLOBAL[divName + '-stored-reference'];
                            existingChart.destroy();
                        }
                        var c = new dojox.charting.Chart2D(divName);
                        PWM_GLOBAL[divName + '-stored-reference'] = c;
                        c.addPlot("default", {type: "Columns", gap:'2'});
                        c.addAxis("x", {});
                        c.addAxis("y", {vertical: true});
                        c.setTheme(dojox.charting.themes.Wetland);
                        c.addSeries("Series 1", values);
                        c.render();
                    }
                }
            });
        });
}

function createCSSClass(selector, style)
{
    // using information found at: http://www.quirksmode.org/dom/w3c_css.html
    // doesn't work in older versions of Opera (< 9) due to lack of styleSheets support
    if(!document.styleSheets) return;
    if(document.getElementsByTagName("head").length == 0) return;
    var stylesheet;
    var mediaType;
    if(document.styleSheets.length > 0)
    {
        for(i = 0; i<document.styleSheets.length; i++)
        {
            if(document.styleSheets[i].disabled) continue;
            var media = document.styleSheets[i].media;
            mediaType = typeof media;
            // IE
            if(mediaType == "string")
            {
                if(media == "" || media.indexOf("screen") != -1)
                {
                    styleSheet = document.styleSheets[i];
                }
            }
            else if(mediaType == "object")
            {
                if(media.mediaText == "" || media.mediaText.indexOf("screen") != -1)
                {
                    styleSheet = document.styleSheets[i];
                }
            }
            // stylesheet found, so break out of loop
            if(typeof styleSheet != "undefined") break;
        }
    }
    // if no style sheet is found
    if(typeof styleSheet == "undefined")
    {
        // create a new style sheet
        var styleSheetElement = document.createElement("style");
        styleSheetElement.type = "text/css";
        // add to <head>
        document.getElementsByTagName("head")[0].appendChild(styleSheetElement);
        // select it
        for(i = 0; i<document.styleSheets.length; i++)
        {
            if(document.styleSheets[i].disabled) continue;
            styleSheet = document.styleSheets[i];
        }
        // get media type
        var media = styleSheet.media;
        mediaType = typeof media;
    }
    // IE
    if(mediaType == "string")
    {
        for(i = 0;i<styleSheet.rules.length;i++)
        {
            // if there is an existing rule set up, replace it
            if(styleSheet.rules[i].selectorText.toLowerCase() == selector.toLowerCase())
            {
                styleSheet.rules[i].style.cssText = style;
                return;
            }
        }
        // or add a new rule
        styleSheet.addRule(selector,style);
    }
    else if(mediaType == "object")
    {
        for(i = 0;i<styleSheet.cssRules.length;i++)
        {
            // if there is an existing rule set up, replace it
            if(styleSheet.cssRules[i].selectorText.toLowerCase() == selector.toLowerCase())
            {
                styleSheet.cssRules[i].style.cssText = style;
                return;
            }
        }
        // or insert new rule
        styleSheet.insertRule(selector + "{" + style + "}", styleSheet.cssRules.length);
    }
}

