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

PWM_MAIN.ajaxTimeout = 120 * 1000;

PWM_MAIN.pageLoadHandler = function() {
    PWM_GLOBAL['localeBundle'] = PWM_GLOBAL['localeBundle'] || [];
    require(["dojo/_base/array","dojo/_base/Deferred","dojo/promise/all"], function(array,Deferred,all){
        var promises = [];
        {
            var clientLoadDeferred = new Deferred();
            PWM_MAIN.loadClientData(function(){clientLoadDeferred.resolve()});
            promises.push(clientLoadDeferred.promise);
        }
        if (typeof PWM_CONFIG !== 'undefined') {
            var clientConfigLoadDeferred = new Deferred();
            PWM_CONFIG.initConfigPage(function(){clientConfigLoadDeferred.resolve()});
            promises.push(clientConfigLoadDeferred.promise);
        }
        {
            var seenBundles = [];
            PWM_GLOBAL['localeBundle'].push('Display');
            array.forEach(PWM_GLOBAL['localeBundle'], function(bundleName){
                if (array.indexOf(seenBundles, bundleName)  == -1) {
                    var displayLoadDeferred = new Deferred();
                    PWM_MAIN.loadLocaleBundle(bundleName,function(){displayLoadDeferred.resolve()});
                    promises.push(displayLoadDeferred.promise);
                    seenBundles.push(bundleName);
                }
            });
        }
        all(promises).then(function () {
            PWM_MAIN.initPage();
        });
    });

};

PWM_MAIN.loadClientData=function(completeFunction) {
    require(["dojo"],function(dojo){
        PWM_GLOBAL['app-data-client-retry-count'] = PWM_GLOBAL['app-data-client-retry-count'] + 1;
        var displayStringsUrl = PWM_GLOBAL['url-context'] + "/public/rest/app-data/client?etag=" + PWM_GLOBAL['clientEtag'];
        dojo.xhrGet({
            url: displayStringsUrl,
            handleAs: 'json',
            timeout: PWM_MAIN.ajaxTimeout,
            headers: { "Accept": "application/json" },
            load: function(data) {
                for (var globalProp in data['data']['PWM_GLOBAL']) {
                    PWM_GLOBAL[globalProp] = data['data']['PWM_GLOBAL'][globalProp];
                }
                console.log('loaded client data');
                if (completeFunction) completeFunction();
            },
            error: function(error) {
                var errorMsg = 'unable to read app-data: ' + error;;
                console.log(errorMsg);
                if (!PWM_VAR['initError']) PWM_VAR['initError'] = errorMsg;
                if (completeFunction) completeFunction();
            }
        });
    });
};

PWM_MAIN.loadLocaleBundle = function(bundleName, completeFunction) {
    require(["dojo"],function(dojo){
        var clientConfigUrl = PWM_GLOBAL['url-context'] + "/public/rest/app-data/strings/" + bundleName;
        dojo.xhrGet({
            url: clientConfigUrl,
            handleAs: 'json',
            timeout: PWM_MAIN.ajaxTimeout,
            headers: { "Accept": "application/json" },
            load: function(data) {
                if (data['error'] == true) {
                    console.error('unable to load locale bundle from ' + clientConfigUrl + ', error: ' + data['errorDetail'])
                } else {
                    PWM_GLOBAL['localeStrings'] = PWM_GLOBAL['localeStrings'] || {};
                    PWM_GLOBAL['localeStrings'][bundleName] = {};
                    for (var settingKey in data['data']) {
                        PWM_GLOBAL['localeStrings'][bundleName][settingKey] = data['data'][settingKey];
                    }
                }
                console.log('loaded locale bundle data for ' + bundleName);
                if (completeFunction) completeFunction();
            },
            error: function(error) {
                var errorMsg = 'unable to load locale bundle from , please reload page (' + error + ')';
                console.log(errorMsg);
                if (!PWM_VAR['initError']) PWM_VAR['initError'] = errorMsg;
                if (completeFunction) completeFunction();
            }
        });
    });
};

PWM_MAIN.initPage = function() {
    for (var j = 0; j < document.forms.length; j++) {
        var loopForm = document.forms[j];
        loopForm.setAttribute('autocomplete', 'off');
        require(["dojo","dojo/on"], function(dojo,on){
            on(loopForm, "reset", function(){
                PWM_MAIN.handleFormClear();
                return false;
            });
        });
    }

    require(["dojo","dojo/on"], function(dojo,on){
        on(document, "keypress", function(event){
            PWM_MAIN.checkForCapsLock(event);
        });
    });

    if (PWM_MAIN.getObject('button_cancel')) {
        PWM_MAIN.getObject('button_cancel').style.visibility = 'visible';
    }

    if (PWM_GLOBAL['pageLeaveNotice'] > 0) {
        require(["dojo","dojo/on"], function(dojo,on){
            on(document, "beforeunload", function(){
                dojo.xhrPost({
                    url: PWM_GLOBAL['url-command'] + "?processAction=pageLeaveNotice&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    preventCache: true,
                    sync: true
                });
            });
        });
    }

    if (PWM_MAIN.getObject('message')) {
        PWM_GLOBAL['message_originalStyle'] = PWM_MAIN.getObject('message').style;
        require(["dojo","dojo/on"], function(dojo,on){
            if(dojo.isIE <= 8){
                return;
            }

            on(window, "resize", function(){ PWM_MAIN.messageDivFloatHandler() });
            on(window, "scroll", function(){ PWM_MAIN.messageDivFloatHandler() });
        });
    }

    if (PWM_MAIN.getObject('header-warning')) {
        require(["dojo/dom", "dojo/_base/fx"],function(dom, fx){
            var args = {node: "header-warning",duration:1000};
            setInterval(function(){fx.fadeOut(args).play()},15*1000);
            setTimeout(function(){setInterval(function(){fx.fadeIn(args).play();},15*1000);},2000);
        });
    }

    require(["dojo/domReady!"],function(){
        if (PWM_GLOBAL['enableIdleTimeout']) {
            PWM_MAIN.IdleTimeoutHandler.initCountDownTimer(PWM_GLOBAL['MaxInactiveInterval']);
        }
        PWM_MAIN.initLocaleSelectorMenu('localeSelectionMenu');
    });

    if (PWM_MAIN.getObject('logoutDiv')) {
        PWM_MAIN.showTooltip({
            id: ["logoutDiv"],
            text: PWM_MAIN.showString("Long_Title_Logout")
        });
    }

    for (var i = 0; i < PWM_GLOBAL['startupFunctions'].length; i++) {
        try {
            PWM_GLOBAL['startupFunctions'][i]();
        } catch(e) {
            console.error('error executing startup function: ' + e);
        }
    }

    PWM_MAIN.TimestampHandler.initAllElements();

    PWM_MAIN.preloadResources();

    console.log('initPage completed');
};

PWM_MAIN.preloadResources = function() {
    var prefix = PWM_GLOBAL['url-resources'] + '/dojo/dijit/themes/';
    var images = [
            prefix + 'a11y/indeterminate_progress.gif',
            prefix + 'nihilo/images/progressBarAnim.gif',
            prefix + 'nihilo/images/progressBarEmpty.png',
            prefix + 'nihilo/images/spriteRoundedIconsSmall.png',
            prefix + 'nihilo/images/titleBar.png'
    ];
    PWM_MAIN.preloadImages(images);
};

PWM_MAIN.showString = function (key, options) {
    options = options || {};
    var bundle = (options['bundle']) ? options['bundle'] : 'Display';
    PWM_GLOBAL['localeStrings'] = PWM_GLOBAL['localeStrings'] || {};
    if (!PWM_GLOBAL['localeStrings'][bundle]) {
        return "UNDEFINED BUNDLE: " + bundle;
    }
    if (PWM_GLOBAL['localeStrings'][bundle][key]) {
        var returnStr = PWM_GLOBAL['localeStrings'][bundle][key];
        for (var i = 0; i < 10; i++) {
            if (options['value' + i]) {
                returnStr = returnStr.replace('%' + i + '%',options['value' + i]);
            }
        }
        return returnStr;
    } else {
        return "UNDEFINED STRING-" + key;
    }
};

PWM_MAIN.goto = function(url,options) {
    options = options || {};
    if (!options['noContext']) {
        if (url.substring(0,1) == '/') {
            url = PWM_GLOBAL['url-context'] + url;
        }
    }
    if (options['addFormID']) {
        if (url.indexOf('pwmFormID') == -1) {
            url += url.indexOf('?') == -1 ? '?' : '&';
            url += "pwmFormID=" + PWM_GLOBAL['pwmFormID'];
        }
    }

    var executeGoto = function() {
        if (options['delay']) {
            setTimeout(function () {
                console.log('redirecting to new url: ' + url);
                window.location = url;
            }, options['delay']);
        } else {
            console.log('redirecting to new url: ' + url);
            window.location = url;
        }
    };

    var hideDialog = options['hideDialog'] = true;
    if (hideDialog) {
        executeGoto();
    } else {
        PWM_MAIN.showWaitDialog({loadFunction:function () {
            executeGoto();
        }});
    }
};

PWM_MAIN.handleFormCancel = function() {
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        var continueUrl = PWM_GLOBAL['url-command'] + '?processAction=continue&pwmFormID=' + PWM_GLOBAL['pwmFormID'];
        window.location = continueUrl;
    }});
};

PWM_MAIN.handleFormSubmit = function (form) {
    PWM_GLOBAL['idle_suspendTimeout'] = true;
    var formElements = form.elements;
    for (var i = 0; i < formElements.length; i++) {
        formElements[i].readOnly = true;
        if (formElements[i].type == 'button' || formElements[i].type == 'submit') {
            formElements[i].disabled = true;
        }
    }

    PWM_MAIN.showWaitDialog({loadFunction:function(){
        form.submit();
    }});
    return false;
};

PWM_MAIN.handleFormClear = function() {
    var focusSet = false;
    var clearableFields = ['text','email','number','password','random','tel','hidden','date','datetime','time','week','month','url','select'];

    require(["dojo/_base/array"],function(array){
        for (var j = 0; j < document.forms.length; j++) {
            for (var i = 0; i < document.forms[j].length; i++) {
                var current = document.forms[j].elements[i];
                if (current.type && array.indexOf(clearableFields,current.type.toLowerCase()) >= 0) {
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
    });
    return false;
};

PWM_MAIN.checkForCapsLock = function(e) {
    require(["dojo","dojo/_base/fx","dojo/domReady!"],function(dojo,fx){
        var capsLockWarningElement = PWM_MAIN.getObject('capslockwarning');
        if (capsLockWarningElement == null) {
            return;
        }

        var capsLockKeyDetected = false;
        {
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
        }

        var displayDuration = 5 * 1000;
        var fadeOutArgs = { node: "capslockwarning", duration: 3 * 1000 };
        var fadeInArgs = { node: "capslockwarning", duration: 200 };

        if(dojo.isIE){
            if (capsLockKeyDetected) {
                capsLockWarningElement.style.display = 'block';
                PWM_GLOBAL['lastCapsLockErrorTime'] = (new Date().getTime());
                setTimeout(function(){
                    if ((new Date().getTime() - PWM_GLOBAL['lastCapsLockErrorTime'] > displayDuration)) {
                        capsLockWarningElement.style.display = 'none';
                    }
                },displayDuration + 500);
            } else {
                capsLockWarningElement.style.display = 'none';
            }
        } else {
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
        }
    });
};

PWM_MAIN.getObject = function(name) {
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
};

PWM_MAIN.trimString = function(sInString) {
    sInString = sInString.replace(/^\s+/g, "");
    // strip leading
    return sInString.replace(/\s+$/g, "");
    // strip trailing
};

PWM_MAIN.showTooltip = function(options){
    options = options || {};

    if (!options['id']) {
        return;
    }

    var id = options['id'] instanceof Array ? options['id'] : [options['id']];
    var position = options['position'] instanceof Array ? options['position'] : null;

    var dojoOptions = {};
    dojoOptions['connectId'] = id;
    dojoOptions['id'] = id[0];
    dojoOptions['label'] = 'text' in options ? options['text'] : "missing text option";

    if (position) {
        dojoOptions['position'] = position;
    }

    if (options['width']) {
        dojoOptions['label'] = '<div style="max-width:' + options['width'] + 'px">' + dojoOptions['label'] + '</div>'
    }

    require(["dijit/Tooltip","dijit/registry"],function(Tooltip){
        PWM_MAIN.clearDijitWidget(id[0]);
        new Tooltip(dojoOptions);
    });

};

PWM_MAIN.clearDijitWidget = function (widgetName) {
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
};

PWM_MAIN.initLocaleSelectorMenu = function(attachNode) {
    if (PWM_MAIN.getObject(attachNode) == null) {
        return;
    }

    require(["dojo","dijit/Menu","dijit/MenuItem","dijit/MenuSeparator","dojo/domReady!"],function(dojo,dijitMenu,dijitMenuItem,dijitMenuSeparator){
        if(dojo.isIE <= 8){ // IE8 and below cant handle the css associated with the locale select menu
            dojo.connect(PWM_MAIN.getObject(attachNode),"click",function(){
                PWM_MAIN.goto("/public/localeselect.jsp");
            });
        } else {

            for (var localeKey in PWM_GLOBAL['localeFlags']) {
                var cssBody = 'background-image: url(' + PWM_GLOBAL['url-context'] +  '/public/resources/flags/png/' + PWM_GLOBAL['localeFlags'][localeKey] + '.png)';
                var cssSelector = '.flagLang_' + localeKey;
                PWM_MAIN.createCSSClass(cssSelector,cssBody);
            }


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
                        PWM_MAIN.showWaitDialog({loadFunction:function(){
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
                        }});
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
                label: PWM_MAIN.showString('Title_LocaleSelect'),
                onClick: function() {
                    PWM_MAIN.showWaitDialog({loadFunction:function() {
                        window.location = PWM_GLOBAL['url-context'] + '/public/localeselect.jsp'
                    }});
                }
            }));
        }
    });
};

PWM_MAIN.showWaitDialog = function(options) {
    require(["dojo","dijit/Dialog","dijit/ProgressBar"],function(dojo,Dialog,ProgressBar){
        options = options || {};
        var requestedLoadFunction = options['loadFunction'];
        options['loadFunction'] = function() {
            var progressBar = new ProgressBar({
                style: '',
                indeterminate:true
            },"progressBar");
            if (requestedLoadFunction) {
                requestedLoadFunction();
            }
        };
        options['title'] = options['title'] || PWM_MAIN.showString('Display_PleaseWait');
        options['text'] = options['text'] || '<div id="progressBar" style="margin: 8px; width: 100%"/>'
        options['width'] = 350;
        options['showOk'] = false;

        /*
        var overlayDiv = document.createElement('div');
        overlayDiv.setAttribute("style","background-color: #000; opacity: .5; filter: alpha(opacity=50); position: absolute; top: 0; left: 0; width: 100%; height: 100%;z-index: 10;");
        document.body.appendChild(overlayDiv);
        */

        PWM_MAIN.showDialog(options);
    });
};

PWM_MAIN.showDialog = function(options) {
    options = options || {};
    var title = options['title'] || 'DialogTitle';
    var text = options['text'] || 'DialogBody';
    var okAction = 'okAction' in options ? options['okAction'] : function(){};
    var cancelAction = 'cancelAction' in options ? options['cancelAction'] : function(){};
    var loadFunction = 'loadFunction' in options ? options['loadFunction'] : function(){};
    var width = options['width'] || 300;
    var showOk = 'showOk' in options ? options['showOk'] : true;
    var showCancel = 'showCancel' in options ? options['showCancel'] : false;
    var showClose = 'showClose' in options ? options['showClose'] : false;
    var allowMove = 'allowMove' in options ? ['allowMove'] : false;

    PWM_VAR['dialog_okAction'] = okAction;
    PWM_VAR['dialog_cancelAction'] = cancelAction;
    PWM_VAR['dialog_loadFunction'] = loadFunction;

    var bodyText = '';
    bodyText += text;
    if (showOk) {
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="PWM_MAIN.closeWaitDialog();PWM_VAR[\'dialog_okAction\']()" id="dialog_ok_button">'
            + '<span class="btn-icon fa fa-forward"></span>'
            + PWM_MAIN.showString('Button_OK') + '</button>  ';
    }
    if (showCancel) {
        bodyText += '<button class="btn" onclick="PWM_MAIN.closeWaitDialog();PWM_VAR[\'dialog_cancelAction\']()" id="dialog_cancel_button">'
            + '<span class="btn-icon fa fa-backward"></span>'
            + PWM_MAIN.showString('Button_Cancel') + '</button>  ';
    }

    if (width > 0) {
        bodyText = '<div style="max-width: ' + width + 'px; width: ' + width + 'px">' + bodyText + '</div>';
    }
    require(["dojo","dijit/Dialog"],function(dojo,Dialog){
        var idName = 'dialogPopup';
        PWM_MAIN.clearDijitWidget(idName);
        var theDialog = new Dialog({
            id: idName,
            closable: showClose,
            draggable: allowMove,
            title: title,
            content: bodyText
        });
        if (!showClose) {
            dojo.style(theDialog.closeButtonNode, "display", "none");
        }
        dojo.connect(theDialog,"onShow",null,loadFunction);
        theDialog.show();
    });
};

PWM_MAIN.showEula = function(requireAgreement, agreeFunction) {
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
        bodyText += '<input type="button" class="btn" value="' + PWM_MAIN.showString('Button_Agree') + '" onclick="PWM_GLOBAL[\'eulaAgreed\']=true;PWM_MAIN.clearDijitWidget(\'dialogPopup\');PWM_GLOBAL[\'dialog_agreeAction\']()"/>';
        bodyText += '<input type="button" class="btn" value="' + PWM_MAIN.showString('Button_Cancel') + '" onclick="PWM_MAIN.closeWaitDialog()"/>';
    } else {
        bodyText += '<input type="button" class="btn" value="' + PWM_MAIN.showString('Button_OK') + '" onclick="PWM_MAIN.closeWaitDialog()"/>';
    }
    bodyText += '</div>'

    PWM_MAIN.clearDijitWidget('dialogPopup');
    require(["dijit/Dialog"], function(Dialog){
        new Dialog({
            title: "End User License Agreement",
            id: 'dialogPopup',
            content: bodyText
        }).show();
    });
};

PWM_MAIN.showConfirmDialog = function(options) {
    options = options || {};
    options['showCancel'] = true;
    options['title'] = 'title' in options ? options['title'] : PWM_MAIN.showString('Button_Confirm');
    PWM_MAIN.showDialog(options);
};

PWM_MAIN.closeWaitDialog = function() {
    require(["dojo","dijit/Dialog","dijit/ProgressBar"],function(dojo,Dialog,ProgressBar){
        PWM_MAIN.clearDijitWidget('dialogPopup');
    });
};

PWM_MAIN.clearError=function() {
    PWM_GLOBAL['messageStatus'] = '';
    PWM_MAIN.doShow('messageStatus','\u00a0');
};

PWM_MAIN.showInfo=function(infoMsg) {
    PWM_GLOBAL['messageStatus'] = 'message-info';
    PWM_MAIN.doShow('message-info',infoMsg);
};

PWM_MAIN.showError=function(errorMsg) {
    PWM_GLOBAL['messageStatus'] = 'message-error';
    PWM_MAIN.doShow('message-error',errorMsg);
};

PWM_MAIN.showSuccess=function(successMsg) {
    PWM_GLOBAL['messageStatus'] = 'message-success';
    PWM_MAIN.doShow('message-success',successMsg);
};

PWM_MAIN.doShow = function(destClass, message, fromFloatHandler) {
    var messageElement = PWM_MAIN.getObject("message");
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
    require(["dojo","dojo/dom-style"],function(dojo,domStyle){
        if(dojo.isIE <= 8){ // only IE7 and below

            messageElement.className = "message " + destClass;
        } else {
            try {
                // create a temp element and place it on the page to figure out what the destination color should be
                var tempDivElement = document.createElement('div');
                tempDivElement.className = "message " + destClass;
                tempDivElement.style.display = "none";
                tempDivElement.id = "tempDivElement";
                messageElement.appendChild(tempDivElement);
                var destStyle = window.getComputedStyle(tempDivElement, null);
                var destBackground = destStyle.backgroundColor;
                var destColor = destStyle.color;
                messageElement.removeChild(tempDivElement);

                dojo.animateProperty({
                    node:"message",
                    duration: 500,
                    properties: {
                        backgroundColor: destBackground,
                        color: destColor
                    }
                }).play();
            } catch (e) {
                messageElement.className = "message " + destClass;
            }
            if (!fromFloatHandler) {
                PWM_MAIN.messageDivFloatHandler();
            }
        }
    });
};

PWM_MAIN.createCSSClass = function(selector, style) {
    // using information found at: http://www.quirksmode.org/dom/w3c_css.html
    // doesn't work in older versions of Opera (< 9) due to lack of styleSheets support
    if(!document.styleSheets) return;
    if(document.getElementsByTagName("head").length == 0) return;
    var styleSheet;
    var mediaType;
    if(document.styleSheets.length > 0)
    {
        for(var i = 0; i<document.styleSheets.length; i++)
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
                try {
                    if(media.mediaText == "" || media.mediaText.indexOf("screen") != -1)
                    {
                        styleSheet = document.styleSheets[i];
                    }
                } catch (e) { /* noop */ }
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
            try {
                if(styleSheet.cssRules[i].selectorText && styleSheet.rules[i].selectorText.toLowerCase() == selector.toLowerCase())
                {
                    styleSheet.rules[i].style.cssText = style;
                    return;
                }
            } catch (e) {
                console.log('error while checking existing rules during cssCreate:' + e);
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
            if(styleSheet.cssRules[i].selectorText && styleSheet.cssRules[i].selectorText.toLowerCase() == selector.toLowerCase())
            {
                styleSheet.cssRules[i].style.cssText = style;
                return;
            }
        }
        // or insert new rule
        styleSheet.insertRule(selector + "{" + style + "}", styleSheet.cssRules.length);
    }
};

PWM_MAIN.flashDomElement = function(flashColor,elementName,durationMS) {
    if (!PWM_MAIN.getObject(elementName)) {
        return;
    }

    require(["dojo","dojo/window","dojo/domReady!"],function(dojo) {
        var originalBGColor = PWM_MAIN.getRenderedStyle(elementName,'background-color');
        PWM_MAIN.getObject(elementName).style.backgroundColor = flashColor;
        dojo.animateProperty({
            node:elementName,
            duration: durationMS,
            properties: { backgroundColor: originalBGColor}
        }).play();
    });
};

PWM_MAIN.getRenderedStyle = function(el,styleProp) {
    var x = document.getElementById(el);
    if (x.currentStyle) {
        return x.currentStyle[styleProp];
    }

    if (window.getComputedStyle) {
        return document.defaultView.getComputedStyle(x,null).getPropertyValue(styleProp);
    }

    return null;
};

PWM_MAIN.elementInViewport = function(el, includeWidth) {
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
};

PWM_MAIN.messageDivFloatHandler = function() {
    var messageObj = PWM_MAIN.getObject('message');
    var messageWrapperObj = PWM_MAIN.getObject('message_wrapper');
    if (!messageObj || !messageWrapperObj) {
        return;
    }

    if (messageObj.style.display == 'none') {
        return;
    }

    var doFloatDisplay = !(PWM_MAIN.elementInViewport(messageWrapperObj,false) || PWM_GLOBAL['messageStatus'] == '');
    if (PWM_GLOBAL['setting_alwaysFloatMessages']) {
        doFloatDisplay = PWM_GLOBAL['messageStatus'] != '';
    }

    if (PWM_GLOBAL['message_scrollToggle'] != doFloatDisplay) {
        PWM_GLOBAL['message_scrollToggle'] = doFloatDisplay;

        if (doFloatDisplay) {
            messageObj.style.position = 'fixed';
            messageObj.style.top = '-3px';
            messageObj.style.left = '0';
            messageObj.style.width = '100%';
            messageObj.style.zIndex = "100";
            messageObj.style.textAlign = "center";
            messageObj.style.backgroundColor = 'black';
            PWM_MAIN.doShow(PWM_GLOBAL['messageStatus'],messageObj.innerHTML,true);
        } else {
            messageObj.style.cssText = '';
            PWM_MAIN.doShow(PWM_GLOBAL['messageStatus'],messageObj.innerHTML,true);
        }
    }
};

PWM_MAIN.pwmFormValidator = function(validationProps, reentrant) {
    var CONSOLE_DEBUG = true;

    var serviceURL = validationProps['serviceURL'];
    var readDataFunction = validationProps['readDataFunction'];
    var processResultsFunction = validationProps['processResultsFunction'];
    var messageWorking = validationProps['messageWorking'] ? validationProps['messageWorking'] : PWM_MAIN.showString('Display_PleaseWait');
    var typeWaitTimeMs = validationProps['typeWaitTimeMs'] ? validationProps['typeWaitTimeMs'] : PWM_GLOBAL['client.ajaxTypingWait'];
    var ajaxTimeout = validationProps['ajaxTimeout'] ? validationProps['ajaxTimeout'] : PWM_GLOBAL['client.ajaxTypingTimeout'];
    var showMessage = 'showMessage' in validationProps ? validationProps['showMessage'] : true;
    var completeFunction = 'completeFunction' in validationProps ? validationProps['completeFunction'] : function(){};


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
            completeFunction();
            return;
        }
    }

    if (!reentrant) {
        PWM_GLOBAL['validationLastType'] = new Date().getTime();
    }

    // check to see if user is still typing.  if yes, then come back later.
    if (new Date().getTime() - PWM_GLOBAL['validationLastType'] < typeWaitTimeMs) {
        if (showMessage) {
            PWM_MAIN.showInfo(PWM_MAIN.showString('Display_TypingWait'));
        }
        setTimeout(function(){PWM_MAIN.pwmFormValidator(validationProps, true)}, typeWaitTimeMs + 1);
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
    if (showMessage) {
        setTimeout(function () {
            if (PWM_GLOBAL['validationInProgress'] == true) {
                PWM_MAIN.showInfo(messageWorking);
            }
        }, 5);
    }

    serviceURL += serviceURL.indexOf('?') > 0 ? '&' : '?';
    serviceURL += "pwmFormID=" + PWM_GLOBAL['pwmFormID'];

    require(["dojo"],function(dojo){
        var formDataString = dojo.toJson(formData);
        if (CONSOLE_DEBUG) console.log('FormValidator: sending form data to server... ' + formDataString);
        dojo.xhrPost({
            url: serviceURL,
            postData: formDataString,
            headers: {"Accept":"application/json","X-RestClientKey":PWM_GLOBAL['restClientKey']},
            contentType: "application/json;charset=utf-8",
            encoding: "utf-8",
            handleAs: "json",
            dataType: "json",
            preventCache: true,
            timeout: ajaxTimeout,
            error: function(errorObj) {
                PWM_GLOBAL['validationInProgress'] = false;
                if (showMessage) {
                    PWM_MAIN.showInfo(PWM_MAIN.showString('Display_CommunicationError'));
                }
                if (CONSOLE_DEBUG) console.log('pwmFormValidator: error connecting to service: ' + errorObj);
                processResultsFunction(null);
                completeFunction();
            },
            load: function(data){
                PWM_GLOBAL['validationInProgress'] = false;
                delete PWM_GLOBAL['validationLastType'];
                PWM_GLOBAL['validationCache'][formKey] = data;
                if (CONSOLE_DEBUG) console.log('pwmFormValidator: successful read, data added to cache');
                PWM_MAIN.pwmFormValidator(validationProps, true);
            }
        });
    });
};

PWM_MAIN.preloadImages = function(imgArray){
    var newimages=[]
    var arr=(typeof imgArray!="object")? [imgArray] : imgArray //force arr parameter to always be an array
    for (var i=0; i<arr.length; i++){
        newimages[i]=new Image();
        newimages[i].src=arr[i];
    }
};


PWM_MAIN.isEmpty = function(o) {
    for (var key in o) if (o.hasOwnProperty(key)) return false;
    return true;
};

PWM_MAIN.itemCount = function(o) {
    var i = 0;
    for (var key in o) if (o.hasOwnProperty(key)) i++;
    return i;
};

PWM_MAIN.toggleFullscreen = function(iconObj,divName) {
    var obj = PWM_MAIN.getObject(divName);

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
};

PWM_MAIN.updateLoginContexts = function() {
    var ldapProfileElement = PWM_MAIN.getObject('ldapProfile');
    var contextElement = PWM_MAIN.getObject('context');
    if (contextElement && ldapProfileElement) {
        var selectedProfile = ldapProfileElement.options[ldapProfileElement.selectedIndex].value;
        contextElement.options.length = 0;
        for (var key in PWM_GLOBAL['ldapProfiles'][selectedProfile]) {
            (function(key) {
                var display = PWM_GLOBAL['ldapProfiles'][selectedProfile][key];
                var optionElement = document.createElement('option');
                optionElement.setAttribute('value', key);
                optionElement.appendChild(document.createTextNode(display));
                contextElement.appendChild(optionElement);
            }(key));
        }
    }
};

//---------- show/hide password handler

var ShowHidePasswordHandler = {};

ShowHidePasswordHandler.idSuffix = '-eye-icon';
ShowHidePasswordHandler.state = {};
ShowHidePasswordHandler.toggleRevertTimeout = 30 * 1000;
ShowHidePasswordHandler.debugOutput = false;

ShowHidePasswordHandler.initAllForms = function() {
    if (!PWM_GLOBAL['setting-showHidePasswordFields']) {
        return;
    }

    require(["dojo/query"], function(query){
        var inputFields = query('input[type="password"]');
        for (var i = 0; i < inputFields.length; i++) {
            var field = inputFields[i];
            if (field.id) {
                if (ShowHidePasswordHandler.debugOutput) console.log('adding show/hide option on fieldID=' + field.id);
                ShowHidePasswordHandler.init(field.id);
            }
        }
    });
};

ShowHidePasswordHandler.init = function(nodeName) {
    if (!PWM_GLOBAL['setting-showHidePasswordFields']) {
        return;
    }

    ShowHidePasswordHandler.toggleRevertTimeout = PWM_GLOBAL['client.pwShowRevertTimeout'] || ShowHidePasswordHandler.toggleRevertTimeout;
    var eyeId = nodeName + ShowHidePasswordHandler.idSuffix;
    if (PWM_MAIN.getObject(eyeId)) {
        return;
    }

    require(["dojo/dom-construct", "dojo/on"], function(domConstruct, on){
        var node = PWM_MAIN.getObject(nodeName);
        var divElement = document.createElement('div');
        divElement.id = eyeId;
        divElement.onclick = function(){ShowHidePasswordHandler.toggle(nodeName)};
        divElement.style.cursor = 'pointer';
        divElement.style.visibility = 'hidden';
        domConstruct.place(divElement,node,'after');

        ShowHidePasswordHandler.state[nodeName] = true;
        ShowHidePasswordHandler.setupTooltip(nodeName, false);

        on(node, "keyup, input", function(){
            if (ShowHidePasswordHandler.debugOutput) console.log("keypress event on node " + nodeName)
            ShowHidePasswordHandler.renderIcon(nodeName);
            ShowHidePasswordHandler.setupTooltip(nodeName);
        });

    });
};

ShowHidePasswordHandler.renderIcon = function(nodeName) {
    if (ShowHidePasswordHandler.debugOutput) console.log("calling renderIcon on node " + nodeName);
    var node = PWM_MAIN.getObject(nodeName);
    var eyeId = nodeName + ShowHidePasswordHandler.idSuffix;
    var eyeNode = PWM_MAIN.getObject(eyeId);
    if (node && node.value && node.value.length > 0) {
        eyeNode.style.visibility = 'visible';
    } else {
        eyeNode.style.visibility = 'hidden';
    }
    eyeNode.className = eyeNode.className; //ie8 force-rendering hack
}

ShowHidePasswordHandler.toggle = function(nodeName) {
    if (ShowHidePasswordHandler.state[nodeName]) {
        ShowHidePasswordHandler.show(nodeName);
    } else {
        ShowHidePasswordHandler.hide(nodeName);
    }
};

ShowHidePasswordHandler.hide = function(nodeName) {
    ShowHidePasswordHandler.state[nodeName] = true;
    ShowHidePasswordHandler.changeInputTypeField(PWM_MAIN.getObject(nodeName),'password');
    ShowHidePasswordHandler.setupTooltip(nodeName);
    ShowHidePasswordHandler.renderIcon(nodeName);
};

ShowHidePasswordHandler.show = function(nodeName) {
    ShowHidePasswordHandler.state[nodeName] = false;
    ShowHidePasswordHandler.changeInputTypeField(PWM_MAIN.getObject(nodeName),'text');
    setTimeout(function(){
        if (!ShowHidePasswordHandler.state[nodeName]) {
            ShowHidePasswordHandler.toggle(nodeName);
        }
    },ShowHidePasswordHandler.toggleRevertTimeout);
    ShowHidePasswordHandler.setupTooltip(nodeName);
    ShowHidePasswordHandler.renderIcon(nodeName);
};

ShowHidePasswordHandler.changeInputTypeField = function(object, type) {
    require(["dojo","dojo/_base/lang", "dojo/dom", "dojo/dom-attr"], function(dojo, lang, dom, attr){
        var newObject = null;
        if(dojo.isIE <= 8){ // IE doesn't support simply changing the type of object
            newObject = document.createElement(object.nodeName);
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
            if (object.onchange) newObject.onchange = object.onchange;
            if (object.disabled) newObject.disabled = object.disabled;
            if (object.readonly) newObject.readonly = object.readonly;
        } else {
            newObject = lang.clone(object);
            attr.set(newObject, "type", type);
        }

        object.parentNode.replaceChild(newObject, object);
        return newObject;
    });
};

ShowHidePasswordHandler.setupTooltip = function(nodeName) {
    if (ShowHidePasswordHandler.debugOutput) console.log('begin setupTooltip');
    var state = ShowHidePasswordHandler.state[nodeName];
    var eyeNodeId = nodeName + ShowHidePasswordHandler.idSuffix;
    PWM_MAIN.clearDijitWidget(eyeNodeId );
    require(["dojo","dijit","dijit/Tooltip"],function(dojo,dijit,Tooltip){
        if (state) {
            new Tooltip({
                connectId: [eyeNodeId],
                label: PWM_MAIN.showString('Button_Show')
            });
            dojo.removeClass(eyeNodeId);
            dojo.addClass(eyeNodeId,["fa","fa-eye"]);
            if (ShowHidePasswordHandler.debugOutput) console.log('set class to fa-eye');
        } else {
            new Tooltip({
                connectId: [eyeNodeId],
                label: PWM_MAIN.showString('Button_Hide')
            });
            dojo.removeClass(eyeNodeId);
            dojo.addClass(eyeNodeId,["fa","fa-eye-slash"]);
            if (ShowHidePasswordHandler.debugOutput) console.log('set class to fa-slash');
        }
    });
};

//---------- idle timeout handler

PWM_MAIN.IdleTimeoutHandler = {};

PWM_MAIN.IdleTimeoutHandler.SETTING_LOOP_FREQUENCY = 1000;   // milliseconds
PWM_MAIN.IdleTimeoutHandler.SETTING_WARN_SECONDS = 30;       // seconds
PWM_MAIN.IdleTimeoutHandler.SETTING_POLL_SERVER_SECONDS = 10; //
PWM_MAIN.IdleTimeoutHandler.timeoutSeconds = 0; // idle timeout time permitted
PWM_MAIN.IdleTimeoutHandler.lastActivityTime = new Date(); // time at which we are "idled out"
PWM_MAIN.IdleTimeoutHandler.lastPingTime = new Date(); // date at which the last ping occured
PWM_MAIN.IdleTimeoutHandler.realWindowTitle = "";
PWM_MAIN.IdleTimeoutHandler.warningDisplayed = false;

PWM_MAIN.IdleTimeoutHandler.initCountDownTimer = function(secondsRemaining) {
    PWM_MAIN.IdleTimeoutHandler.timeoutSeconds = secondsRemaining;
    PWM_MAIN.IdleTimeoutHandler.lastPingTime = new Date();
    PWM_MAIN.IdleTimeoutHandler.realWindowTitle = document.title;
    PWM_MAIN.IdleTimeoutHandler.resetIdleCounter();
    setInterval(function(){PWM_MAIN.IdleTimeoutHandler.pollActivity()}, PWM_MAIN.IdleTimeoutHandler.SETTING_LOOP_FREQUENCY); //poll scrolling
    require(["dojo/on"], function(on){
        on(document, "click", function(){PWM_MAIN.IdleTimeoutHandler.resetIdleCounter()});
        on(document, "keypress", function(){PWM_MAIN.IdleTimeoutHandler.resetIdleCounter()});
        on(document, "scroll", function(){PWM_MAIN.IdleTimeoutHandler.resetIdleCounter()});
    });
};

PWM_MAIN.IdleTimeoutHandler.resetIdleCounter = function() {
    PWM_MAIN.IdleTimeoutHandler.lastActivityTime = new Date();
    PWM_MAIN.IdleTimeoutHandler.closeIdleWarning();
    PWM_MAIN.IdleTimeoutHandler.pollActivity();
};

PWM_MAIN.IdleTimeoutHandler.pollActivity = function() {
    var secondsRemaining = PWM_MAIN.IdleTimeoutHandler.calcSecondsRemaining();
    var idleDisplayString = PWM_MAIN.IdleTimeoutHandler.makeIdleDisplayString(secondsRemaining);
    var idleStatusFooter = PWM_MAIN.getObject("idle_status");
    if (idleStatusFooter != null) {
        idleStatusFooter.firstChild.nodeValue = idleDisplayString;
    }

    var warningDialogText = PWM_MAIN.getObject("IdleDialogWindowIdleText");
    if (warningDialogText != null) {
        warningDialogText.firstChild.nodeValue = idleDisplayString;
    }

    if (secondsRemaining < 0) {
        if (!PWM_GLOBAL['idle_suspendTimeout']) {
            PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
            PWM_GLOBAL['idle_suspendTimeout'] = true;
            window.location = PWM_GLOBAL['url-logout'];
        } else {
            try { PWM_MAIN.getObject('idle_wrapper').style.visibility = 'none'; } catch(e) { /* noop */ }
        }
        return;
    }

    var pingAgoMs = (new Date()).getTime() - PWM_MAIN.IdleTimeoutHandler.lastPingTime.getTime();
    if (pingAgoMs > (PWM_MAIN.IdleTimeoutHandler.timeoutSeconds - PWM_MAIN.IdleTimeoutHandler.SETTING_POLL_SERVER_SECONDS) * 1000) {
        PWM_MAIN.IdleTimeoutHandler.pingServer();
    }

    if (secondsRemaining < PWM_MAIN.IdleTimeoutHandler.SETTING_WARN_SECONDS) {
        if (!PWM_GLOBAL['idle_suspendTimeout']) {
            PWM_MAIN.IdleTimeoutHandler.showIdleWarning();
            if (secondsRemaining % 2 == 0) {
                document.title = PWM_MAIN.IdleTimeoutHandler.realWindowTitle;
            } else {
                document.title = idleDisplayString;
            }
        }
    }
};

PWM_MAIN.IdleTimeoutHandler.pingServer = function() {
    PWM_MAIN.IdleTimeoutHandler.lastPingTime = new Date();
    var pingURL = PWM_GLOBAL['url-command'] + "?processAction=idleUpdate&time=" + new Date().getTime() + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    require(["dojo"], function(dojo){
        dojo.xhrPost({url:pingURL,sync:false});
    });
};

PWM_MAIN.IdleTimeoutHandler.calcSecondsRemaining = function() {
    var timeoutTime = PWM_MAIN.IdleTimeoutHandler.lastActivityTime.getTime() + (PWM_MAIN.IdleTimeoutHandler.timeoutSeconds * 1000)
    var amount = timeoutTime - (new Date()).getTime();
    amount = Math.floor(amount / 1000);
    return amount;
};

PWM_MAIN.IdleTimeoutHandler.makeIdleDisplayString = function(amount) {
    if (amount < 1) {
        return "";
    }

    var output = "";

    var days = Math.floor(amount / 86400);

    amount = amount % 86400;
    var hours = Math.floor(amount / 3600);

    amount = amount % 3600;
    var mins = Math.floor(amount / 60);

    amount = amount % 60;
    var secs = Math.floor(amount);

    // write number of days
    var positions = 0;
    if (days != 0) {
        output += days + " ";
        if (days != 1) {
            output += PWM_MAIN.showString('Display_Days');
        } else {
            output += PWM_MAIN.showString('Display_Day');
        }
        positions++;
    }

    // write number of hours
    if (days != 0 || hours != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += hours + " ";
        if (hours != 1) {
            output += PWM_MAIN.showString('Display_Hours');
        } else {
            output += PWM_MAIN.showString('Display_Hour');
        }
        positions ++;
    }

    // write number of minutes
    if (positions < 2) {
        if (days != 0 || hours != 0 || mins != 0) {
            if (output.length > 0) {
                output += ", ";
            }
            output += mins + " ";
            if (mins != 1) {
                output += PWM_MAIN.showString('Display_Minutes');
            } else {
                output += PWM_MAIN.showString('Display_Minute');
            }
            positions++;
        }
    }


    // write number of seconds
    if (positions < 2) {
        if (mins < 4) {
            if (output.length > 0) {
                output += ", ";
            }

            output += secs + " ";

            if (secs != 1) {
                output += PWM_MAIN.showString('Display_Seconds');
            } else {
                output += PWM_MAIN.showString('Display_Second');
            }
        }
    }

    output = PWM_MAIN.showString('Display_IdleTimeout') + " " + output;
    return output;
};

PWM_MAIN.IdleTimeoutHandler.showIdleWarning = function() {
    if (!PWM_MAIN.IdleTimeoutHandler.warningDisplayed) {
        PWM_MAIN.IdleTimeoutHandler.warningDisplayed = true;

        var dialogBody = PWM_MAIN.showString('Display_IdleWarningMessage') + '<br/><br/><span id="IdleDialogWindowIdleText">&nbsp;</span>';
        require(["dijit/Dialog"],function(){
            var theDialog = new dijit.Dialog({
                title: PWM_MAIN.showString('Display_IdleWarningTitle'),
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

PWM_MAIN.IdleTimeoutHandler.closeIdleWarning = function() {
    PWM_MAIN.clearDijitWidget('idleDialog');
    document.title = PWM_MAIN.IdleTimeoutHandler.realWindowTitle;
    PWM_MAIN.IdleTimeoutHandler.warningDisplayed = false;
};

PWM_MAIN.TimestampHandler = PWM_MAIN.TimestampHandler || {};
PWM_MAIN.TimestampHandler.Key_ToggleState = false;
PWM_MAIN.TimestampHandler.ElementList = [];

PWM_MAIN.TimestampHandler.initAllElements = function() {
    require(["dojo/query", "dojo/NodeList-dom"], function(query){
        query(".timestamp").forEach(function(node){
            PWM_MAIN.TimestampHandler.initElement(node);
        });
    });
};

PWM_MAIN.TimestampHandler.testIfStringIsTimestamp = function(input, trueFunction) {
    require(["dojo","dojo/date/stamp"], function(dojo,IsoDate) {
        input = dojo.trim(input);
        var dateObj = IsoDate.fromISOString(input);
        if (dateObj) {
            trueFunction(dateObj);
        }
    });
};

PWM_MAIN.TimestampHandler.initElement = function(element) {
    if (!element) {
        return
    }

    if (element.getAttribute('data-timestamp-init') === 'true') {
        return;
    }

    require(["dojo"], function(dojo) {
        var innerText = dojo.attr(element, 'innerHTML');
        innerText = dojo.trim(innerText);
        PWM_MAIN.TimestampHandler.testIfStringIsTimestamp(innerText, function (dateObj) {
            element.setAttribute('data-timestamp-original', innerText);
            require(["dojo", "dojo/on"], function (dojo, on) {
                on(element, "click", function (event) {
                    PWM_MAIN.TimestampHandler.toggleAllElements();
                });
            });

            if (!dojo.hasClass(element,"timestamp")) {
                dojo.addClass(element,"timestamp");
            }

            element.setAttribute('data-timestamp-init', 'true');
            PWM_MAIN.TimestampHandler.ElementList.push(element);
            PWM_MAIN.TimestampHandler.toggleElement(element);
        });
    });
};

PWM_MAIN.TimestampHandler.toggleAllElements = function() {
    for (var el in PWM_MAIN.TimestampHandler.ElementList) {
        var element = PWM_MAIN.TimestampHandler.ElementList[el];
        if (document.body.contains(element)) {
            PWM_MAIN.TimestampHandler.toggleElement(element);
        } else {
            delete PWM_MAIN.TimestampHandler.ElementList[el];
        }
    }
};

PWM_MAIN.TimestampHandler.toggleElement = function(element) {
    require(["dojo","dojo/date/stamp","dojo/date/locale"], function(dojo,IsoDate,LocaleDate) {
        var localized = element.getAttribute('data-timestamp-state') === 'localized';
        if (localized) {
            dojo.attr(element,'innerHTML',element.getAttribute('data-timestamp-original'));
            element.setAttribute('data-timestamp-state','iso');
        } else {
            var isoDateStr = element.getAttribute('data-timestamp-original');
            var date = IsoDate.fromISOString(isoDateStr);
            var localizedStr = LocaleDate.format(date,{formatLength:'long'});
            dojo.attr(element,'innerHTML',localizedStr);
            element.setAttribute('data-timestamp-state','localized');
        }
    })
};

PWM_MAIN.addPwmFormIDtoURL = function(url) {
    if (!url || url.length < 1) {
        return '';
    }
    url += url.contains('?') ? '&' : '?';
    url += "pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    return url;
};