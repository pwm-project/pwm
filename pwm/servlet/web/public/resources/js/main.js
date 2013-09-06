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

var PWM_GLOBAL = PWM_GLOBAL || {};
var PWM_STRINGS = PWM_STRINGS || {};

function pwmPageLoadHandler() {
    require(["dojo"],function(dojo){
        var displayStringsUrl = PWM_GLOBAL['url-context'] + "/public/rest/app-data/client";
        dojo.xhrGet({
            url: displayStringsUrl,
            handleAs: 'json',
            timeout: 30 * 1000,
            headers: { "Accept": "application/json" },
            load: function(data) {
                for (var stringProp in data['data']['PWM_STRINGS']) {
                    PWM_STRINGS[stringProp] = data['data']['PWM_STRINGS'][stringProp];
                }
                for (var globalProp in data['data']['PWM_GLOBAL']) {
                    PWM_GLOBAL[globalProp] = data['data']['PWM_GLOBAL'][globalProp];
                }
                initPwmPage();
            },
            error: function(error) {
                console.log('unable to read app-data: ' + error);
                initPwmPage();
            }
        });
    });
}

function initPwmPage() {
    for (var j = 0; j < document.forms.length; j++) {
        var loopForm = document.forms[j];
        loopForm.setAttribute('autocomplete', 'off');
        require(["dojo","dojo/on"], function(dojo,on){
            on(loopForm, "reset", function(){
                handleFormClear();
                return false;
            });
        });
    }

    require(["dojo","dojo/on"], function(dojo,on){
        on(window, "keypress", function(event){
            checkForCapsLock(event);
        });
    });

    if (getObject('button_cancel')) {
        getObject('button_cancel').style.visibility = 'visible';
    }

    if (PWM_GLOBAL['pageLeaveNotice'] > 0) {
        require(["dojo","dojo/on"], function(dojo,on){
            on(window, "beforeunload", function(){
                dojo.xhrPost({
                    url: PWM_GLOBAL['url-command'] + "?processAction=pageLeaveNotice&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    preventCache: true,
                    sync: true
                });
            });
        });
    }

    if (getObject('message')) {
        PWM_GLOBAL['message_originalStyle'] = getObject('message').style;
        require(["dojo/on"], function(on){
            on(window, "resize", function(){messageDivFloatHandler()});
            on(window, "scroll", function(){messageDivFloatHandler()});
        });
    }

    if (getObject('header-warning')) {
        require(["dojo/dom", "dojo/_base/fx"],function(dom, fx){
            var args = {node: "header-warning",duration:1000};
            setInterval(function(){fx.fadeOut(args).play()},15*1000);
            setTimeout(function(){setInterval(function(){fx.fadeIn(args).play();},15*1000);},2000);
        });
    }

    require(["dojo/domReady!"],function(){
        if (PWM_GLOBAL['enableIdleTimeout']) {
            IdleTimeoutHandler.initCountDownTimer(PWM_GLOBAL['MaxInactiveInterval']);
        }
        initLocaleSelectorMenu('localeSelectionMenu');
    });

    if (getObject('logoutDiv')) {
        require(["dojo/domReady!","dijit/Tooltip"],function(dojo,Tooltip){
            new Tooltip({
                connectId: ["logoutDiv"],
                label: showString("Long_Title_Logout")
            });
        });
    }

    require(["dojo/domReady!","dijit/Dialog","dijit/ProgressBar"],function(){ /*preload*/
        var prefix = PWM_GLOBAL['url-resources'] + '/dojo/dijit/themes/';
        var images = [prefix + 'a11y/indeterminate_progress.gif',
            prefix + 'nihilo/images/progressBarAnim.gif',
            prefix + 'nihilo/images/progressBarEmpty.png',
            prefix + 'nihilo/images/spriteRoundedIconsSmall.png',
            prefix + 'nihilo/images/titleBar.png']
        preloadImages(images);
    });

    for (var i = 0; i < PWM_GLOBAL['startupFunctions'].length; i++) {
        PWM_GLOBAL['startupFunctions'][i]();
    }
}



function showString(key) {
    if (PWM_STRINGS[key]) {
        return PWM_STRINGS[key];
    } else {
        return "UNDEFINED STRING-" + key;
    }
}

function handleFormCancel() {
    showWaitDialog(null,null,function(){
        var continueUrl = PWM_GLOBAL['url-command'] + '?processAction=continue&pwmFormID=' + PWM_GLOBAL['pwmFormID'];
        window.location = continueUrl;
    });
}

function handleFormSubmit(buttonID, form) {
    PWM_GLOBAL['idle_suspendTimeout'] = true;
    var submitButton = getObject(buttonID);
    if (submitButton != null) {
        getObject(buttonID).value = showString('Display_PleaseWait');
        getObject(buttonID).disabled = true;

        var formElements = submitButton.form.elements;
        for (var i = 0; i < formElements.length; i++) {
            formElements[i].readOnly = true;
        }
    }

    showWaitDialog(null,null,function(){form.submit();});
    return false;
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

function checkForCapsLock(e) {
    require(["dojo","dojo/_base/fx","dojo/domReady!"],function(dojo,fx){
        if(dojo.isIE){
            return;
        }

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
            fx.fadeIn(fadeInArgs).play();
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
            fx.fadeOut(fadeOutArgs).play();
        }
    });
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

function clearDijitWidget(widgetName) {
    require(["dijit/registry"],function(registry){
        var oldDijitNode = registry.byId(widgetName);
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
    if (getObject(attachNode) == null) {
        return;
    }

    for (var localeKey in PWM_GLOBAL['localeFlags']) {
        var cssBody = 'background-image: url(' + PWM_GLOBAL['url-context'] +  '/public/resources/flags/png/' + PWM_GLOBAL['localeFlags'][localeKey] + '.png)';
        var cssSelector = '.flagLang_' + localeKey;
        createCSSClass(cssSelector,cssBody);
    }


    require(["dojo/domReady!"],function(){
        require(["dojo","dijit/Menu","dijit/MenuItem","dijit/MenuSeparator"],function(dojo, dijitMenu, dijitMenuItem, dijitMenuSeparator){
            var localeData = PWM_GLOBAL['localeInfo'];
            var pMenu = new dijitMenu({
                targetNodeIds: [attachNode],
                leftClickToOpen: true
            });
            pMenu.startup();

            var loopFunction = function(pMenu, localeKey, localeDisplayName, localeIconClass) {
                pMenu.addChild(new dijitMenuItem({
                    label: localeDisplayName,
                    iconClass: localeIconClass,
                    onClick: function() {
                        showWaitDialog();
                        var pingURL = PWM_GLOBAL['url-command'] + "?processAction=idleUpdate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&" + PWM_GLOBAL['paramName.locale'] + "=" + localeKey;
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

            pMenu.addChild(new dijitMenuSeparator());
            pMenu.addChild(new dijitMenuItem({
                label: showString('Title_LocaleSelect'),
                onClick: function() {
                    showWaitDialog(null,null,function(){
                        window.location = PWM_GLOBAL['url-context'] + '/public/localeselect.jsp'
                    });
                }
            }));
        });
    });
}

function showWaitDialog(title, body, loadFunction) {
    if (title == null) {
        title=showString('Display_PleaseWait');
    }
    require(["dojo","dijit/Dialog","dijit/ProgressBar"],function(dojo,Dialog,ProgressBar){
        var idName = 'dialogPopup';
        if (!loadFunction) {
            loadFunction = function(){};
        }
        clearDijitWidget(idName);
        if (body == null || body.length < 1) {
            //body = '<div id="WaitDialogBlank"/>';
            body = '<div id="progressBar" style="margin: 8px; width: 100%"/>'
        }
        var theDialog = new Dialog({
            id: idName,
            closable: false,
            draggable: false,
            title: title,
            style: "width: 300px",
            content: body
        });
        dojo.style(theDialog.closeButtonNode,"display","none");
        dojo.connect(theDialog,"onShow",null,loadFunction);
        theDialog.show();
        var progressBar = new ProgressBar({
            style: '',
            indeterminate:true
        },"progressBar");
    });
}

function showDialog(title, text, nextAction) {
    var titleText = title == null ? "" : title;
    PWM_GLOBAL['dialog_nextAction'] = nextAction ? nextAction : function(){};
    var bodyText = '';
    bodyText += '<div><p>';
    bodyText += text;
    bodyText += '</p></div>';
    bodyText += '<br/>';
    bodyText += '<button class="btn" onclick="closeWaitDialog();PWM_GLOBAL[\'dialog_nextAction\']()">' + showString('Button_OK') + '</button>  ';
    showWaitDialog(titleText,bodyText);
}

function showEula(requireAgreement, agreeFunction) {
    if (agreeFunction && PWM_GLOBAL['eulaAgreed']) {
        agreeFunction();
        return;
    }
    var eulaLocation = PWM_GLOBAL['url-context'] + '/public/resources/text/eula.html';
    PWM_GLOBAL['dialog_agreeAction'] = agreeFunction ? agreeFunction : function(){};
    var bodyText = '<iframe width="600" height="400" src="' + eulaLocation + '">';
    bodyText += '</iframe>';
    bodyText += '<div style="width: 100%; text-align: center">';
    if (requireAgreement) {
        bodyText += '<input type="button" class="btn" value="' + showString('Button_Agree') + '" onclick="PWM_GLOBAL[\'eulaAgreed\']=true;clearDijitWidget(\'dialogPopup\');PWM_GLOBAL[\'dialog_agreeAction\']()"/>';
        bodyText += '<input type="button" class="btn" value="' + showString('Button_Cancel') + '" onclick="closeWaitDialog()"/>';
    } else {
        bodyText += '<input type="button" class="btn" value="' + showString('Button_OK') + '" onclick="closeWaitDialog()"/>';
    }
    bodyText += '</div>'

    clearDijitWidget('dialogPopup');
    require(["dijit/Dialog"], function(Dialog){
        new Dialog({
            title: "End User License Agreement",
            id: 'dialogPopup',
            content: bodyText
        }).show();
    });
}

function showConfirmDialog(title, text, trueAction, falseAction) {
    var titleText = title == null ? showString('Button_Confirm') : title;
    PWM_GLOBAL['confirm_true_action'] = trueAction ? trueAction : function(){};
    PWM_GLOBAL['confirm_false_action'] = falseAction ? falseAction : function(){};
    var bodyText = '';
    bodyText += '<div><p>';
    bodyText += text;
    bodyText += '</p></div>';
    bodyText += '<br/>';
    bodyText += '<button class="btn" onclick="closeWaitDialog();PWM_GLOBAL[\'confirm_true_action\']()">' + showString('Button_OK') + '</button>  ';
    bodyText += '<button class="btn" onclick="closeWaitDialog();PWM_GLOBAL[\'confirm_false_action\']()">' + showString('Button_Cancel') + '</button>  ';
    showWaitDialog(titleText,bodyText);
}

function closeWaitDialog() {
    require(["dojo","dijit/Dialog","dijit/ProgressBar"],function(dojo,Dialog,ProgressBar){
        clearDijitWidget('dialogPopup');
    });
}

function showPwmHealth(parentDivID, options, refreshNow) {
    var inputOpts = options || PWM_GLOBAL['showPwmHealthOptions'] || {};
    PWM_GLOBAL['showPwmHealthOptions'] = options;
    var refreshUrl = inputOpts['sourceUrl'] || PWM_GLOBAL['url-restservice'] + "/health";
    var showRefresh = inputOpts['showRefresh'];
    var showTimestamp = inputOpts['showTimestamp'];
    var refreshTime = inputOpts['refreshTime'] || 10 * 1000;
    var finishFunction = inputOpts['finishFunction'];
    console.log('starting showPwmHealth: refreshTime=' + refreshTime);
    require(["dojo"],function(dojo){
        var parentDiv = dojo.byId(parentDivID);

        if (PWM_GLOBAL['healthCheckInProgress']) {
            return;
        }


        PWM_GLOBAL['healthCheckInProgress'] = "true";

        if (refreshNow) {
            parentDiv.innerHTML = '<div id="WaitDialogBlank" style="margin-top: 20px; margin-bottom: 20px"/>';
        }

        refreshUrl += refreshUrl.indexOf('?') > 0 ? '&' : '?';
        refreshUrl += "pwmFormID=" + PWM_GLOBAL['pwmFormID'];

        if (refreshNow) {
            refreshUrl += "&refreshImmediate=true";
        }

        dojo.xhrGet({
            url: refreshUrl,
            handleAs: "json",
            headers: { "Accept": "application/json" },
            timeout: 60 * 1000,
            preventCache: true,
            load: function(data) {
                PWM_GLOBAL['pwm-health'] = data['data']['overall'];
                var healthRecords = data['data']['records'];
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
                if (showTimestamp || showRefresh) {
                    htmlBody += '<tr><td colspan="3" style="text-align:center;">';
                    if (showTimestamp) {
                        htmlBody += new Date(data['data']['timestamp']).toLocaleString() + '&nbsp;&nbsp;&nbsp;&nbsp;';
                    }
                    if (showRefresh) {
                        htmlBody += '<a href="#"; onclick="showPwmHealth(\'' + parentDivID + '\',null,true)">refresh</a>';
                    }
                    htmlBody += "</td></tr>";
                }

                htmlBody += '</table>';
                parentDiv.innerHTML = htmlBody;
                PWM_GLOBAL['healthCheckInProgress'] = false;
                if (refreshTime > 0) {
                    setTimeout(function() {
                        showPwmHealth(parentDivID, options);
                    }, refreshTime);
                }
                if (finishFunction) {
                    finishFunction();
                }
            },
            error: function(error) {
                if (error != null) {
                    console.log('error reaching server: ' + error);
                }
                var htmlBody = '<div style="text-align:center; background-color: #d20734">';
                htmlBody += '<br/><span style="font-weight: bold;">unable to load health data from server</span></br>';
                htmlBody += '<br/>' + new Date().toLocaleString() + '&nbsp;&nbsp;&nbsp;';
                if (showRefresh) {
                    htmlBody += '<a href="#" onclick="showPwmHealth(\'' + parentDivID + '\',null,true)">retry</a><br/><br/>';
                }
                htmlBody += '</div>';
                parentDiv.innerHTML = htmlBody;
                PWM_GLOBAL['healthCheckInProgress'] = false;
                PWM_GLOBAL['pwm-health'] = 'WARN';
                if (refreshTime > 0) {
                    setTimeout(function() {
                        showPwmHealth(parentDivID, options);
                    }, refreshTime);
                }
                if (finishFunction) {
                    finishFunction();
                }
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
    setInterval(function(){IdleTimeoutHandler.pollActivity()}, IdleTimeoutHandler.SETTING_LOOP_FREQUENCY); //poll scrolling
    require(["dojo/on"], function(on){
        on(document, "click", function(){IdleTimeoutHandler.resetIdleCounter()});
        on(document, "keypress", function(){IdleTimeoutHandler.resetIdleCounter()});
        on(document, "scroll", function(){IdleTimeoutHandler.resetIdleCounter()});
    });
};

IdleTimeoutHandler.resetIdleCounter = function() {
    var idleSeconds = IdleTimeoutHandler.calcIdleSeconds();
    IdleTimeoutHandler.closeIdleWarning();
    try {
        getObject("idle_status").firstChild.nodeValue = IdleTimeoutHandler.makeIdleDisplayString(idleSeconds);
    } catch (e) {
        console.log("unable to update idle_status html node: " + e);
    }

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
        } else {
            try { getObject('idle_wrapper').style.visibility = 'none'; } catch(e) { /* noop */ }
        }

    }

    if (idleSeconds < IdleTimeoutHandler.SETTING_WARN_SECONDS) {
        if (!PWM_GLOBAL['idle_suspendTimeout']) {
            IdleTimeoutHandler.showIdleWarning();
            if (idleSeconds % 2 == 0) {
                document.title = PWM_GLOBAL['real-window-title'];
            } else {
                document.title = idleDisplayString;
            }
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
            output += showString('Display_Days');
        } else {
            output += showString('Display_Day');
        }
    }

    // write number of hours
    if (days != 0 || hours != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += hours + " ";
        if (hours != 1) {
            output += showString('Display_Hours');
        } else {
            output += showString('Display_Hour');
        }
    }

    // write number of minutes
    if (days != 0 || hours != 0 || mins != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += mins + " ";
        if (mins != 1) {
            output += showString('Display_Minutes');
        } else {
            output += showString('Display_Minute');
        }
    }

    // write number of seconds
    if (mins < 4) {
        if (output.length > 0) {
            output += ", ";
        }

        output += secs + " ";

        if (secs != 1) {
            output += showString('Display_Seconds');
        } else {
            output += showString('Display_Second');
        }
    }

    output = showString('Display_IdleTimeout') + " " + output;
    return output;
};

IdleTimeoutHandler.showIdleWarning = function() {
    if (!PWM_GLOBAL['idle_warningDisplayed']) {
        PWM_GLOBAL['idle_warningDisplayed'] = true;

        var dialogBody = showString('Display_IdleWarningMessage') + '<br/><br/><span id="IdleDialogWindowIdleText">&nbsp;</span>';
        require(["dijit/Dialog"],function(){
            var theDialog = new dijit.Dialog({
                title: showString('Display_IdleWarningTitle'),
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
    PWM_GLOBAL['messageStatus'] = '';
    doShow('messageStatus','\u00a0');
}

function showInfo(infoMsg)
{
    PWM_GLOBAL['messageStatus'] = 'message-info';
    doShow('message-info',infoMsg);
}

function showError(errorMsg)
{
    PWM_GLOBAL['messageStatus'] = 'message-error';
    doShow('message-error',errorMsg);
}

function showSuccess(successMsg)
{
    PWM_GLOBAL['messageStatus'] = 'message-success';
    doShow('message-success',successMsg);
}

function doShow(destClass, message) {
    var messageElement = getObject("message");
    if (messageElement == null || messageElement.firstChild == null || messageElement.firstChild.nodeValue == null) {
        return;
    }

    if (destClass == '') {
        require(["dojo/dom", "dojo/_base/fx"],function(dom, fx){
            var fadeArgs = { node: "message", duration: 500 };
            fx.fadeOut(fadeArgs).play();
        });
        return;
    };
    messageElement.firstChild.nodeValue = message;

    try {
        messageElement.style.display = 'inherit'; // doesn't work in older ie browsers
    } catch (e) {
        messageElement.style.display = 'block';
    }

    messageElement.style.opacity = '1';
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
            messageDivFloatHandler();
        }
    });
}

function showStatChart(statName,days,divName) {
    var epsTypes = PWM_GLOBAL['epsTypes'];
    var epsDurations = PWM_GLOBAL['epsDurations'];
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
        function(dojo,dijit,registry){
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
                    for (var loopEpsTypeIndex = 0; loopEpsTypeIndex < epsTypes.length; loopEpsTypeIndex++) { // clear all the gauges
                        var loopEpsName = epsTypes[loopEpsTypeIndex] + '';
                        for (var loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                            var loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                            var loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                            if (getObject(loopEpsID) != null) {
                                if (registry.byId(loopEpsID)) {
                                    registry.byId(loopEpsID).setAttribute('value',0);
                                }
                            }
                        }
                    }
                },
                load: function(data) {
                    {// gauges
                        console.log('Beginning stats update process...');
                        data = data['data'];
                        var activityCount = 0;
                        for (var loopEpsIndex = 0; loopEpsIndex < epsTypes.length; loopEpsIndex++) {
                            var loopEpsName = epsTypes[loopEpsIndex] + '';
                            for (var loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                                var loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                                var loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                                var loopFieldEpsID = "FIELD_" + loopEpsName + "_" + loopEpsDuration;
                                var loopEpsValue = data['EPS'][loopEpsName + "_" + loopEpsDuration];
                                var loopEpmValue = (loopEpsValue * 60).toFixed(3);
                                var loopTop = PWM_GLOBAL['client.activityMaxEpsRate'];
                                if (loopEpsDuration == "HOURLY") {
                                    activityCount += loopEpsValue;
                                }
                                if (getObject(loopFieldEpsID) != null) {
                                    getObject(loopFieldEpsID).innerHTML = loopEpmValue;
                                }
                                if (getObject(loopEpsID) != null) {
                                    console.log('EpsID=' + loopEpsID + ', ' + 'Eps=' + loopEpsValue + ', ' + 'Epm=' + loopEpmValue);
                                    if (registry.byId(loopEpsID)) {
                                        registry.byId(loopEpsID).setAttribute('value',loopEpmValue);
                                        registry.byId(loopEpsID).setAttribute('max',loopTop);
                                    } else {
                                        var glossyCircular = new dojox.gauges.GlossyCircularGauge({
                                            background: [255, 255, 255, 0],
                                            noChange: true,
                                            value: loopEpmValue,
                                            max: loopTop,
                                            needleColor: '#FFDC8B',
                                            majorTicksInterval: Math.abs(loopTop / 10),
                                            minorTicksInterval: Math.abs(loopTop / 10),
                                            id: loopEpsID,
                                            width: 200,
                                            height: 150
                                        }, dojo.byId(loopEpsID));
                                        glossyCircular.startup();
                                    }
                                }
                            }
                        }
                        PWM_GLOBAL['epsActivityCount'] = activityCount;
                    }
                    if (divName != null && getObject(divName)) { // stats chart
                        var values = [];
                        for(var key in data['nameData']) {
                            var value = data['nameData'][key];
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

function flashDomElement(flashColor,elementName,durationMS) {
    if (!getObject(elementName)) {
        return;
    }

    require(["dojo","dojo/window","dojo/domReady!"],function(dojo) {
        var originalBGColor = getRenderedStyle(elementName,'background-color');
        getObject(elementName).style.backgroundColor = flashColor;
        dojo.animateProperty({
            node:elementName,
            duration: durationMS,
            properties: { backgroundColor: originalBGColor}
        }).play();
    });
}

function getRenderedStyle(el,styleProp) {
    var x = document.getElementById(el);
    if (x.currentStyle) {
        return x.currentStyle[styleProp];
    }

    if (window.getComputedStyle) {
        return document.defaultView.getComputedStyle(x,null).getPropertyValue(styleProp);
    }

    return null;
}

function elementInViewport(el, includeWidth) {
    var top = el.offsetTop;
    var left = el.offsetLeft;
    var width = el.offsetWidth;
    var height = el.offsetHeight;

    while(el.offsetParent) {
        el = el.offsetParent;
        top += el.offsetTop;
        left += el.offsetLeft;
    }

    var pageY = (typeof(window.pageYOffset)=='number') ? window.pageYOffset : document.documentElement.scrollTop;
    var pageX = (typeof(window.pageXOffset)=='number') ? window.pageXOffset : document.documentElement.scrollLeft;

    return includeWidth ? (
        top >= pageY && (top + height) <= (pageY + window.innerHeight) &&
            left >= pageX &&
            (left + width) <= (pageX + window.innerWidth)
        ) : (
        top >= pageY && (top + height) <= (pageY + window.innerHeight)
        );
}

function messageDivFloatHandler() { // called by message.jsp
    require(["dojo","dojo/dom", "dojo/_base/fx", "dojo/on", "dojo/dom-style", "dojo/domReady!"],function(dojo){
        if(dojo.isIE <= 8){
            return;
        }

        var messageObj = getObject('message');
        var messageWrapperObj = getObject('message_wrapper');
        if (!messageObj || !messageWrapperObj) {
            return;
        }

        if (messageObj.style.display == 'none') {
            return;
        }

        if (PWM_GLOBAL['message_scrollToggle'] != elementInViewport(messageWrapperObj) + PWM_GLOBAL['messageStatus']) {
            PWM_GLOBAL['message_scrollToggle'] = elementInViewport(messageWrapperObj) + PWM_GLOBAL['messageStatus'];

            if (elementInViewport(messageWrapperObj,false) || PWM_GLOBAL['messageStatus'] == '') {
                messageObj.style.cssText = '';
                doShow(PWM_GLOBAL['messageStatus'],messageObj.innerHTML);
            } else {
                messageObj.style.position = 'fixed';
                messageObj.style.top = '-3px';
                messageObj.style.left = '0';
                messageObj.style.width = '100%';
                messageObj.style.zIndex = "100";
                messageObj.style.textAlign = "center";
                messageObj.style.backgroundColor = 'black';
                doShow(PWM_GLOBAL['messageStatus'],messageObj.innerHTML);
            }
        }
    });
}

function pwmFormValidator(validationProps, reentrant)
{
    var TYPE_WAIT_TIME_MS = PWM_GLOBAL['client.ajaxTypingWait'];
    var AJAX_TIMEOUT = PWM_GLOBAL['client.ajaxTypingTimeout'];
    var CONSOLE_DEBUG = true;

    var serviceURL = validationProps['serviceURL'] + (validationProps['serviceURL'].indexOf('?') == -1 ? '?' : '&' ) + "pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    var readDataFunction = validationProps['readDataFunction'];
    var processResultsFunction = validationProps['processResultsFunction'];
    var messageWorking = validationProps['messageWorking'] ? validationProps['messageWorking'] : showString('Display_PleaseWait');

    if (CONSOLE_DEBUG) console.log("pwmFormValidator: beginning...");
    //init vars;
    if (!PWM_GLOBAL['validationCache']) {
        PWM_GLOBAL['validationCache'] = {};
    }

    // check if data is in cache, if it is just process it.
    var formData = readDataFunction();
    var formKey = "";
    for (var key in formData) {formKey += formData[key] + "-";}

    {
        var cachedResult = PWM_GLOBAL['validationCache'][formKey];
        if (cachedResult != null) {
            processResultsFunction(cachedResult);
            if (CONSOLE_DEBUG) console.log('pwmFormValidator: processed cached data, exiting');
            return;
        }
    }

    if (!reentrant) {
        PWM_GLOBAL['validationLastType'] = new Date().getTime();
    }

    // check to see if user is still typing.  if yes, then come back later.
    if (new Date().getTime() - PWM_GLOBAL['validationLastType'] < TYPE_WAIT_TIME_MS) {
        showInfo(showString('Display_TypingWait'));
        setTimeout(function(){pwmFormValidator(validationProps, true)}, TYPE_WAIT_TIME_MS + 1);
        if (CONSOLE_DEBUG) console.log('pwmFormValidator: sleeping while waiting for typing to finish, will retry...');
        return;
    }
    if (CONSOLE_DEBUG) console.log('pwmFormValidator: user no longer typing, continuing..');

    //check to see if a validation is already in progress, if it is then ignore keypress.
    if (PWM_GLOBAL['validationInProgress'] == true) {
        if (CONSOLE_DEBUG) console.log('pwmFormValidator: waiting for a previous validation to complete, exiting...');
        return;
    }
    PWM_GLOBAL['validationInProgress'] = true;

    // show in-progress message if load takes too long.
    setTimeout(function(){ if (PWM_GLOBAL['validationInProgress']==true) { showInfo(messageWorking); } },5);
    var formDataString = dojo.toJson(formData);

    require(["dojo"],function(dojo){
        if (CONSOLE_DEBUG) console.log('pwmFormValidator: sending form data to server...');
        dojo.xhrPost({
            url: serviceURL,
            postData: formDataString,
            headers: {"Accept":"application/json"},
            contentType: "application/json;charset=utf-8",
            encoding: "utf-8",
            handleAs: "json",
            dataType: "json",
            preventCache: true,
            timeout: AJAX_TIMEOUT,
            error: function(errorObj) {
                PWM_GLOBAL['validationInProgress'] = false;
                showInfo(showString('Display_CommunicationError'));
                if (CONSOLE_DEBUG) console.log('pwmFormValidator: error connecting to service: ' + errorObj);
                processResultsFunction(null);
            },
            load: function(data){
                PWM_GLOBAL['validationInProgress'] = false;
                delete PWM_GLOBAL['validationLastType'];
                PWM_GLOBAL['validationCache'][formKey] = data;
                if (CONSOLE_DEBUG) console.log('pwmFormValidator: successful read, data added to cache');
                pwmFormValidator(validationProps, true);
            }
        });
    });
}

function setCookie(c_name,value,exseconds)
{
    var exdate=new Date();
    exdate.setTime(exdate.getTime() + (exseconds * 1000));
    var c_value=escape(value) + ((exseconds==null) ? "" : "; expires="+exdate.toUTCString());
    document.cookie=c_name+"=" + c_value;
}

function getCookie(c_name)
{
    var i,x,y,ARRcookies=document.cookie.split(";");
    for (i=0;i<ARRcookies.length;i++)
    {
        x=ARRcookies[i].substr(0,ARRcookies[i].indexOf("="));
        y=ARRcookies[i].substr(ARRcookies[i].indexOf("=")+1);
        x=x.replace(/^\s+|\s+$/g,"");
        if (x==c_name)
        {
            return unescape(y);
        }
    }
}

function preloadImages(arr){
    var newimages=[]
    var arr=(typeof arr!="object")? [arr] : arr //force arr parameter to always be an array
    for (var i=0; i<arr.length; i++){
        newimages[i]=new Image()
        newimages[i].src=arr[i]
    }
}


function isEmpty(o) {
    for (var key in o) if (o.hasOwnProperty(key)) return false;
    return true;
}

function itemCount(o) {
    var i = 0;
    for (var key in o) if (o.hasOwnProperty(key)) i++;
    return i;
}

var ShowHidePasswordHandler = {};
ShowHidePasswordHandler.idSuffix = '-eye-icon';
ShowHidePasswordHandler.toggleState = {};
ShowHidePasswordHandler.toggleRevertTimeout = 15 * 1000;

ShowHidePasswordHandler.initAllForms = function() {
    if (!PWM_GLOBAL['setting-showHidePasswordFields']) {
        return;
    }

    require(["dojo/query"], function(query){
        var inputFields = query('input[type="password"]');
        for (var i = 0; i < inputFields.length; i++) {
            var field = inputFields[i];
            if (field.id) {
                console.log('adding show/hide option on fieldID=' + field.id);
                ShowHidePasswordHandler.init(field.id);
            }
        }
    });
}

ShowHidePasswordHandler.init = function(nodeName) {
    if (!PWM_GLOBAL['setting-showHidePasswordFields']) {
        return;
    }
    var eyeId = nodeName + ShowHidePasswordHandler.idSuffix;
    if (getObject(eyeId)) {
        return;
    }

    require(["dojo/dom-construct", "dojo/_base/connect"], function(domConstruct, connect){
        node = getObject(nodeName);
        var divElement = document.createElement('div');
        divElement.className = 'icon-eye icon-eye-open';
        divElement.id = eyeId;
        divElement.onclick = function(){ShowHidePasswordHandler.toggle(nodeName)};
        divElement.style.cursor = 'pointer';
        divElement.style.visibility = 'hidden';
        domConstruct.place(divElement,node,'after');

        ShowHidePasswordHandler.toggleState[nodeName] = true;
        ShowHidePasswordHandler.setupTooltip(nodeName, false);

        connect.connect(node, "onkeyup", function(){
            var node = getObject(nodeName);
            if (node && node.value && node.value.length > 0) {
                divElement.style.visibility = 'visible';
            } else {
                divElement.style.visibility = 'hidden';
            }
        });
    });
};

ShowHidePasswordHandler.toggle = function(nodeName) {
    var state = ShowHidePasswordHandler.toggleState[nodeName];
    if (state) {
        ShowHidePasswordHandler.changeInputTypeField(getObject(nodeName),'text');
        setTimeout(function(){
            if (!ShowHidePasswordHandler.toggleState[nodeName]) {
                ShowHidePasswordHandler.toggle(nodeName);
            }
        },ShowHidePasswordHandler.toggleRevertTimeout);
    } else {
        ShowHidePasswordHandler.changeInputTypeField(getObject(nodeName),'password');
    }
    ShowHidePasswordHandler.setupTooltip(nodeName, state);
    ShowHidePasswordHandler.toggleState[nodeName] = !state;
};

ShowHidePasswordHandler.changeInputTypeField = function(object, type) {
    require(["dojo/_base/lang", "dojo/dom", "dojo/dom-attr"], function(lang, dom, attr){
        var newObject = lang.clone(object);
        attr.set(newObject, "type", type);
        object.parentNode.replaceChild(newObject, object);
        return newObject;
    });
}

ShowHidePasswordHandler.setupTooltip = function(nodeName, passwordsMasked) {
    var eyeNodeId = nodeName + ShowHidePasswordHandler.idSuffix;
    clearDijitWidget(eyeNodeId );
    require(["dijit","dijit/Tooltip"],function(dijit,Tooltip){
        if (passwordsMasked) {
            new Tooltip({
                connectId: [eyeNodeId],
                label: PWM_STRINGS['Button_Hide']
            });
            getObject(eyeNodeId).className = 'icon-eye icon-eye-close';
        } else {
            new Tooltip({
                connectId: [eyeNodeId],
                label: PWM_STRINGS['Button_Show']
            });
            getObject(eyeNodeId).className = 'icon-eye icon-eye-open';
        }
    });
};

function toggleFullscreen(iconObj,divName) {
    var obj = getObject(divName);

    var storedStyleName = 'fullscreen-style-' + divName;
    if (PWM_GLOBAL[storedStyleName]) {
        iconObj.className = "icon-fullscreen";
        obj.style = PWM_GLOBAL[storedStyleName];
        delete PWM_GLOBAL[storedStyleName];
    } else {
        PWM_GLOBAL[storedStyleName] = obj.style;
        iconObj.className = "icon-resize-full";
        obj.style.position = 'fixed';
        obj.style.top = '0';
        obj.style.left = '0';
        obj.style.bottom = '0';
        obj.style.right = '0';
        obj.style.zIndex = '100';
        obj.style.background = 'white';
    }
    if (PWM_GLOBAL['displayGrid']) {
        PWM_GLOBAL['displayGrid'].resize();
    }
}
