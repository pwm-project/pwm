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

function saveConfiguration(waitForReload) {
    showWaitDialog('Saving Configuration...', null, function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"ConfigManager?processAction=finishEditing&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                preventCache: true,
                dataType: "json",
                handleAs: "json",
                load: function(data){
                    if (data['error'] == true) {
                        closeWaitDialog();
                        showError(data['errorDetail']);
                    } else {
                        if (waitForReload) {
                            var oldEpoch = data['data']['currentEpoch'];
                            var currentTime = new Date().getTime();
                            showError('Waiting for server restart');
                            waitForRestart(currentTime, oldEpoch);
                        } else {
                            window.location = "ConfigManager";
                        }
                    }
                }
            });
        });
    });
}

function finalizeConfiguration() {
    showWaitDialog(null,null,function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"ConfigManager?processAction=lockConfiguration&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                sync:true,
                dataType: "json",
                handleAs: "json",
                preventCache: true,
                load: function(data) {
                    if (data['error'] == true) {
                        closeWaitDialog();
                        showError(data['errorDetail']);
                    } else {
                        var oldEpoch = data['data']['currentEpoch'];
                        var currentTime = new Date().getTime();
                        showError('Waiting for server restart');
                        waitForRestart(currentTime, oldEpoch);
                    }
                }
            });
        });
    });
}


function waitForRestart(startTime, oldEpoch) {
    require(["dojo"],function(dojo){
        var currentTime = new Date().getTime();
        dojo.xhrGet({
            url:"ConfigManager?processAction=getEpoch",
            preventCache: true,
            timeout: 10 * 1000,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                if (data['error'] == true) {
                    clearDijitWidget('waitDialogID');
                    showError(data['errorDetail']);
                    return;
                }

                var epoch = data['data']['currentEpoch'];
                if (epoch != oldEpoch) {
                    window.location = "ConfigManager"; //refresh page
                    return;
                }
                var diff = currentTime - startTime;
                console.log('oldEpoch=' + oldEpoch + ", currentEpoch=" + epoch + ", difftime=" + diff);
                if (diff > 4 * 60 * 1000) { // timeout
                    alert('Configuration save successful.   Unable to restart PWM, please restart the java application server.');
                    showError('PWM Server has not restarted (timeout)');
                } else {
                    showError('Waiting for server restart, server has not yet restarted (' + (diff) + ' ms)');
                    setTimeout(function() {
                        waitForRestart(startTime, oldEpoch)
                    }, Math.random() * 1000);
                }
            },
            error: function(error) {
                setTimeout(function() {
                    waitForRestart(startTime, oldEpoch)
                }, 1000);
                console.log('Waiting for server restart, unable to contact server: ' + error);
            }
        });
    });
}

function handleResetClick(settingKey) {
    showConfirmDialog(null,'Are you sure you want to reset this setting to the default value?',function(){
        resetSetting(settingKey);
        window.location = "ConfigManager";
    });
}

function startNewConfigurationEditor(template) {
    showWaitDialog('Loading...','');
    require(["dojo"],function(dojo){
        dojo.xhrGet({
            url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&getTemplate=" + template,
            preventCache: true,
            error: function(errorObj) {
                showError("error starting configuration editor: " + errorObj);
            },
            load: function() {
                window.location = "ConfigManager?processAction=editMode&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + '&mode=SETTINGS';
            }
        });
    });
}

function startConfigurationEditor() {
    require(["dojo"],function(dojo){
        if(dojo.isIE <= 7){ // only IE8 and below
            alert('Internet Explorer 7 and below is not able to edit the configuration.  Please use a newer version of Internet Explorer or a different browser.');
            document.forms['cancelEditing'].submit();
        } else {
            showWaitDialog('Loading...','');
            window.location = "ConfigManager?processAction=editMode&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + '&mode=SETTINGS';
        }
    });
}

function readInitialTextBasedValue(key) {
    require(["dijit/registry"],function(registry){
        readSetting(key, function(dataValue) {
            getObject('value_' + key).value = dataValue;
            getObject('value_' + key).disabled = false;
            registry.byId('value_' + key).set('disabled', false);
            registry.byId('value_' + key).startup();
            try {registry.byId('value_' + key).validate(false);} catch (e) {}
            try {registry.byId('value_verify_' + key).validate(false);} catch (e) {}
        });
    });
}

function writeConfigurationNotes() {
    require(["dojo","dijit/Dialog"],function(dojo){
        var value = getObject('configNotesDialog').value;
        PWM_GLOBAL['configurationNotes'] = value;
        showWaitDialog();
        dojo.xhrPost({
            url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&updateNotesText=true",
            postData: dojo.toJson(value),
            contentType: "application/json;charset=utf-8",
            dataType: "json",
            handleAs: "text",
            load: function(data){
                closeWaitDialog();
                buildMenuBar();
            },
            error: function(errorObj) {
                closeWaitDialog();
                showError("error saving notes text: " + errorObj);
                buildMenuBar();
            }
        });
    });
}

function showConfigurationNotes() {
    var idName = 'configNotesDialog';
    var bodyText = '<textarea cols="40" rows="10" style="width: 575px; height: 300px; resize:none" onchange="writeConfigurationNotes()" id="' + idName + '">';
    bodyText += 'Loading...';
    bodyText += '</textarea>';
    bodyText += '<button onclick="writeConfigurationNotes()" class="btn">' + PWM_STRINGS['Button_OK'] + '</button>';

    require(["dijit/Dialog"],function(Dialog){
        var theDialog = new Dialog({
            id: 'dialogPopup',
            title: 'Configuration Notes',
            style: "width: 600px;",
            content: bodyText
        });
        theDialog.show();
        getObject(idName).value = PWM_GLOBAL['configurationNotes'];
        setCookie("hide-warn-shownotes","true", 60 * 60);
    });
}

function setConfigurationPassword(password) {
    if (password) {
        dojo.xhrPost({
            url:"ConfigManager?processAction=setConfigurationPassword&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            postData: password,
            contentType: "application/text;charset=utf-8",
            dataType: "text",
            handleAs: "text",
            load: function(data){
                clearDijitWidget('changepassword-popup');
                showInfo('Configuration password set successfully.')
                return;
            },
            error: function(errorObj) {
                clearDijitWidget('changepassword-popup');
                return;
                showError("error saving notes text: " + errorObj);
            }
        });
    }

    var writeFunction = 'setConfigurationPassword(getObject(\'password1\').value)';
    ChangePasswordHandler.init('configPw','Configuration Password',writeFunction);
}
