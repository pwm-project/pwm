/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// -------------------------- common elements handler ------------------------------------

var UILibrary = {};
UILibrary.stringEditorDialog = function(options){
    options = options === undefined ? {} : options;
    var title = 'title' in options ? options['title'] : 'Edit Value';
    var instructions = 'instructions' in options ? options['instructions'] : null;
    var completeFunction = 'completeFunction' in options ? options['completeFunction'] : function() {alert('no string editor dialog complete function')};
    var regexString = 'regex' in options && options['regex'] ? options['regex'] : '.+';
    var initialValue = 'value' in options ? options['value'] : '';
    var placeholder = 'placeholder' in options ? options['placeholder'] : '';
    var textarea = 'textarea' in options ? options['textarea'] : false;

    var regexObject = new RegExp(regexString);
    var text = '';
    text += '<div style="visibility: hidden;" id="panel-valueWarning"><span class="pwm-icon pwm-icon-warning message-error"></span>&nbsp;' + PWM_CONFIG.showString('Warning_ValueIncorrectFormat') + '</div>';
    text += '<br/>';

    if (instructions !== null) {
        text += '<div id="panel-valueInstructions">&nbsp;' + options['instructions'] + '</div>';
        text += '<br/>';
    }

    if (textarea) {
        text += '<textarea style="max-width: 480px; width: 480px; height:300px; max-height:300px; overflow-y: auto" class="configStringInput" autofocus required id="addValueDialog_input"></textarea>';
    } else {
        text += '<input style="width: 480px" class="configStringInput" autofocus required id="addValueDialog_input"/>';
    }

    var inputFunction = function() {
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        PWM_MAIN.getObject('panel-valueWarning').style.visibility = 'hidden';

        var value = PWM_MAIN.getObject('addValueDialog_input').value;
        if (value.length > 0) {
            var passedValidation = regexObject  !== null && regexObject.test(value);

            if (passedValidation) {
                PWM_MAIN.getObject('dialog_ok_button').disabled = false;
                PWM_VAR['temp-dialogInputValue'] = PWM_MAIN.getObject('addValueDialog_input').value;
            } else {
                PWM_MAIN.getObject('panel-valueWarning').style.visibility = 'visible';
            }
        }
    };

    var okFunction = function() {
        var value = PWM_VAR['temp-dialogInputValue'];
        completeFunction(value);
    };

    PWM_MAIN.showDialog({
        title:title,
        text:text,
        okAction:okFunction,
        showCancel:true,
        showClose: true,
        allowMove: true,
        dialogClass: 'auto',
        loadFunction:function(){
            PWM_MAIN.getObject('addValueDialog_input').value = initialValue;
            if (regexString && regexString.length > 1) {
                PWM_MAIN.getObject('addValueDialog_input').setAttribute('pattern',regexString);
            }
            if (placeholder && placeholder.length > 1) {
                PWM_MAIN.getObject('addValueDialog_input').setAttribute('placeholder',placeholder);
            }
            inputFunction();
            PWM_MAIN.addEventHandler('addValueDialog_input','input',function(){
                inputFunction();
            });
        }
    });
};

UILibrary.stringArrayEditorDialog = function(options){
    options = options === undefined ? {} : options;
    var title = 'title' in options ? options['title'] : 'Edit Value';
    var instructions = 'instructions' in options ? options['instructions'] : null;
    var completeFunction = 'completeFunction' in options ? options['completeFunction'] : function() {alert('no string array editor dialog complete function')};
    var regexString = 'regex' in options && options['regex'] ? options['regex'] : '.+';
    var initialValues = 'value' in options ? options['value'] : [];
    var placeholder = 'placeholder' in options ? options['placeholder'] : '';
    var maxvalues = 'maxValues' in options ? options['maxValues'] : 10;

    var regexObject = new RegExp(regexString);
    var text = '';
    text += '<div style="visibility: hidden;" id="panel-valueWarning"><span class="pwm-icon pwm-icon-warning message-error"></span>&nbsp;' + PWM_CONFIG.showString('Warning_ValueIncorrectFormat') + '</div>';
    text += '<br/>';

    if (instructions !== null) {
        text += '<div style="margin-left: 10px" id="panel-valueInstructions">' + options['instructions'] + '</div>';
        text += '<br/>';
    }

    text += '<table class="noborder">';
    for (var i in initialValues) {
        text += '<tr class="noborder"><td>';
        text += '<input style="width: 400px" class="configStringInput" pattern="' + regexString + '" autofocus id="value_' + i + '"/></td>';
        if (PWM_MAIN.JSLibrary.itemCount(initialValues) > 1) {
            text += '<td style="width:10px"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-value_' + i + '-deleteRow"></span></td>';
        }
        text += '</tr>';
    }
    text += '</table>';

    if (PWM_MAIN.JSLibrary.itemCount(initialValues) < maxvalues) {
        text += '<br/>';
        text += '<button class="btn" id="button-addRow"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Row</button></td>';
    }

    var readCurrentValues = function() {
        var output = [];
        for (var i in initialValues) {
            var value = PWM_MAIN.getObject('value_' + i).value;
            output.push(value);
        }
        return output;
    };

    var inputFunction = function() {
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        if (PWM_MAIN.JSLibrary.itemCount(initialValues) < maxvalues) {
            PWM_MAIN.getObject('button-addRow').disabled = true;
        }

        PWM_MAIN.getObject('panel-valueWarning').style.visibility = 'hidden';

        var passed = true;
        var allHaveValues = true;

        for (var i in initialValues) {
            (function(iter) {
                var value = PWM_MAIN.getObject('value_'+ iter).value;
                if (value.length > 0) {
                    var passedRegex = regexObject  !== null && regexObject.test(value);
                    if (!passedRegex) {
                        passed = false;
                    }
                } else {
                    allHaveValues = false;
                }
            })(i);
        }

        if (passed && allHaveValues) {
            PWM_MAIN.getObject('dialog_ok_button').disabled = false;
            if (PWM_MAIN.JSLibrary.itemCount(initialValues) < maxvalues) {
                PWM_MAIN.getObject('button-addRow').disabled = false;
            }
        } else if (!passed) {
            PWM_MAIN.getObject('panel-valueWarning').style.visibility = 'visible';
        }

        PWM_VAR['temp-dialogInputValue'] = readCurrentValues();
    };

    var okFunction = function() {
        var value =  PWM_VAR['temp-dialogInputValue'];
        completeFunction(value);
    };

    var deleteRow = function(i) {
        var values = readCurrentValues();
        values.splice(i,1);
        options['value'] = values;
        UILibrary.stringArrayEditorDialog(options);
    };

    PWM_MAIN.showDialog({
        title:title,
        text:text,
        okAction:okFunction,
        showCancel:true,
        showClose: true,
        allowMove: true,
        dialogClass: 'auto',
        loadFunction:function(){
            for (var i in initialValues) {
                (function(iter) {
                    var loopValue = initialValues[iter];
                    PWM_MAIN.getObject('value_' + i).value = loopValue;

                    if (regexString && regexString.length > 1) {
                        PWM_MAIN.getObject('value_' + i).setAttribute('pattern',regexString);
                    }
                    if (placeholder && placeholder.length > 1) {
                        PWM_MAIN.getObject('value_' + i).setAttribute('placeholder',placeholder);
                    }
                    PWM_MAIN.addEventHandler('value_' + i,'input',function(){
                        inputFunction();
                    });

                    PWM_MAIN.addEventHandler('button-value_' + i + '-deleteRow','click',function(){
                        deleteRow(i);
                    });
                })(i);
            }

            if (PWM_MAIN.JSLibrary.itemCount(initialValues) < maxvalues) {
                PWM_MAIN.addEventHandler('button-addRow','click',function(){
                    var values = readCurrentValues();
                    values.push('');
                    options['value'] = values;
                    UILibrary.stringArrayEditorDialog(options);
                });
            }

            inputFunction();
        }
    });
};

UILibrary.addTextValueToElement = function(elementID, input) {
    var element = PWM_MAIN.getObject(elementID);
    if (element) {
        element.innerHTML = '';
        element.appendChild(document.createTextNode(input));
    }
};

UILibrary.addAddLocaleButtonRow = function(parentDiv, keyName, addFunction, existingLocales) {
    existingLocales === undefined ? [] : existingLocales;
    existingLocales.push('en');

    var totalLocales = PWM_MAIN.JSLibrary.itemCount(PWM_GLOBAL['localeInfo']);
    var excludeLocales = PWM_MAIN.JSLibrary.itemCount(existingLocales);

    if (totalLocales < excludeLocales) {
        return;
    }

    var tableRowElement = document.createElement('tr');
    tableRowElement.setAttribute("style","border-width: 0");

    var bodyHtml = '';
    bodyHtml += '<td style="border-width: 0" colspan="5">';
    bodyHtml += '<button type="button" class="btn" id="' + keyName + '-addLocaleButton"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Locale</button>'

    bodyHtml += '</td>';
    tableRowElement.innerHTML = bodyHtml;
    PWM_MAIN.getObject(parentDiv).appendChild(tableRowElement);

    PWM_MAIN.addEventHandler(keyName + '-addLocaleButton','click',function(){
        PWM_MAIN.showLocaleSelectionMenu(function(locale){
            addFunction(locale)
        },{excludeLocales:existingLocales});
    });
};

UILibrary.manageNumericInput = function(elementID, readFunction) {
    var element = PWM_MAIN.getObject(elementID);
    if (!element) {
        return;
    }
    var validChecker = function(value) {
        if (!value) {
            return false;
        }
        if (value.match('^[0-9]*$') === null) {
            return false;
        }
        if (element.hasAttribute('min')) {
            if (value < parseInt(element.getAttribute('min'))) {
                return false;
            }
        }
        if (element.hasAttribute('max')) {
            if (value > parseInt(element.getAttribute('max'))) {
                return false;
            }
        }
        return true;
    };
    PWM_MAIN.addEventHandler(elementID,'input',function(){
        var value = element.value;
        if (validChecker(value)) {
            console.log('valid numerical input value: ' + value);
            readFunction(value);
        } else {
            console.log('invalid numerical input value: ' + value);
        }
    });
};

UILibrary.editLdapDN = function(nextFunction, options) {
    options = options === undefined ? {} : options;
    var profile = 'profile' in options ? options['profile'] : '';
    var currentDN = 'currentDN' in options ? options['currentDN'] : '';
    var processResults = function(data) {
        var body = '';
        if (data['error']) {
            body += '<div>Unable to browse LDAP directory: ' + data['errorMessage'] + '</div>';
            if (data['errorDetail']) {
                body += '<br/><div>' + data['errorDetail'] + '</div>';
            }
            PWM_MAIN.showDialog({title:'Error',text:body,okAction:function(){
                    UILibrary.stringEditorDialog({value:currentDN,completeFunction:nextFunction});
                }});
            return;
        }

        if (!PWM_MAIN.JSLibrary.isEmpty(data['data']['profileList'])) {
            body += '<div style="text-align: center">LDAP Profile <select id="select-profileList"></select></div><br/>';
        }
        body += '<div style="text-align: center">';
        if (currentDN && currentDN.length > 0 ) {
            body += '<div class="selectableDN" data-dn="' + currentDN + '"><a><code>' + currentDN + '</code></a></div>';
        } else {
            body += '<code>[root]</code>';
        }
        body += '</div><br/>';

        body += '<div style="min-width:500px; max-height: 400px; overflow-y: auto; overflow-x:hidden; white-space: nowrap">';
        body += '<table class="noborder">';
        if ('parentDN' in data['data']) {
            var parentDN = data['data']['parentDN'];
            body += '<tr><td style="width:10px" class="navigableDN" data-dn="' + parentDN + '"><span class="pwm-icon pwm-icon-level-up"></span></td>';
            body += '<td title="' + parentDN + '">[parent]</td>';
            body += '</td>';
            body += '</tr>';
        }

        var makeEntryHtml = function(dnInformation,navigable) {
            var loopDN = dnInformation['dn'];
            var entryName = dnInformation['entryName'];
            var out = '';
            if (navigable) {
                out += '<tr><td style="width:10px" class="navigableDN" data-dn="' + loopDN + '"><span class="pwm-icon pwm-icon-level-down"></span></td>';
            } else {
                out += '<tr><td style="width:10px"></td>';
            }
            out += '<td class="selectableDN" data-dn="' + loopDN + '" title="' + loopDN + '"><a><code>' + entryName + '</code></a></td>';
            out += '</tr>';
            return out;
        };

        if (data['data']['navigableDNlist'] && !PWM_MAIN.JSLibrary.isEmpty(data['data']['navigableDNlist'])) {
            var navigableDNlist = data['data']['navigableDNlist'];
            for (var i in navigableDNlist) {
                body += makeEntryHtml(navigableDNlist[i],true);
            }
        }
        if (data['data']['selectableDNlist'] && !PWM_MAIN.JSLibrary.isEmpty(data['data']['selectableDNlist'])) {
            var selectableDNlist = data['data']['selectableDNlist'];
            for (var i in selectableDNlist) {
                body += makeEntryHtml(selectableDNlist[i],false);
            }
        }
        body += '</table></div>';

        if (data['data']['maxResults']) {
            body += '<div class="footnote">' + PWM_MAIN.showString('Display_SearchResultsExceeded') + '</div>';
        }

        body += '<div class="buttonbar"><button class="btn" id="button-editDN"><span class="btn-icon pwm-icon pwm-icon-edit"></span>Edit Text</button>';
        body += '<button class="btn" id="button-refresh"><span class="btn-icon pwm-icon pwm-icon-refresh"></span>Refresh</button>';
        body += '<button class="btn" id="button-clearDN"><span class="btn-icon pwm-icon pwm-icon-times"></span>Clear Value</button></div>';

        PWM_MAIN.showDialog({title:'LDAP Browser',dialogClass:'auto',showOk:false,showClose:true,text:body,loadFunction:function(){
                PWM_MAIN.addEventHandler('button-editDN','click',function(){
                    UILibrary.stringEditorDialog({value:currentDN,completeFunction:nextFunction});
                });
                PWM_MAIN.addEventHandler('button-refresh','click',function(){
                    UILibrary.editLdapDN(nextFunction,{profile:profile,currentDN:currentDN});
                });
                PWM_MAIN.addEventHandler('button-clearDN','click',function(){
                    nextFunction('');
                    PWM_MAIN.closeWaitDialog();
                });

                PWM_MAIN.doQuery(".selectableDN",function(element){
                    var dnValue = element.getAttribute("data-dn");
                    PWM_MAIN.addEventHandler(element,'click',function(){
                        var ldapProfileID = "default";
                        if (document.getElementById("select-profileList")) {
                            ldapProfileID = document.getElementById("select-profileList").value;
                        }

                        nextFunction(dnValue, ldapProfileID);
                        PWM_MAIN.closeWaitDialog();
                    });
                });

                PWM_MAIN.doQuery(".navigableDN",function(element){
                    var dnValue = element.getAttribute("data-dn");
                    PWM_MAIN.addEventHandler(element,'click',function(){
                        UILibrary.editLdapDN(nextFunction,{profile:profile,currentDN:dnValue});
                    });
                });

                if (!PWM_MAIN.JSLibrary.isEmpty(data['data']['profileList'])) {
                    var profileList = data['data']['profileList'];
                    var profileSelect = PWM_MAIN.getObject('select-profileList');
                    for (var i in profileList) {
                        (function(loopProfile) {
                            var optionElement = document.createElement('option');
                            optionElement.innerHTML = loopProfile;
                            optionElement.value = loopProfile;
                            if (loopProfile === profile) {
                                optionElement.selected = true;
                            }
                            profileSelect.appendChild(optionElement);
                        })(profileList[i]);
                    }
                    PWM_MAIN.addEventHandler('select-profileList','change',function(){
                        var value = profileSelect.options[profileSelect.selectedIndex].value;
                        UILibrary.editLdapDN(nextFunction,{profile:value,currentDN:''});
                    });
                }
            }});
    };

    PWM_MAIN.showWaitDialog({loadFunction:function(){
            var content = {};
            content['profile'] = profile;
            content['dn'] = currentDN;
            var url = window.location.pathname + "?processAction=browseLdap";
            PWM_MAIN.ajaxRequest(url,processResults,{content:content});
        }});
};

UILibrary.uploadFileDialog = function(options) {
    options = options === undefined ? {} : options;
    var body = '';

    if ('text' in options) {
        body += options['text'];
    }

    body += '<div id="uploadFormWrapper">';
    body += '<div id="fileList"></div>';
    body += '<input style="width:80%" class="btn" name="uploadFile" type="file" label="Select File" id="uploadFile"/>';
    body += '<div class="buttonbar">';
    body += '<button class="btn" type="button" id="uploadButton" name="Upload" disabled><span class="pwm-icon pwm-icon-upload"></span>';
    body +=  PWM_MAIN.showString('Button_Upload') + '</button></div></div>';

    var currentUrl = window.location.pathname;
    var uploadUrl = 'url' in options ? options['url'] : currentUrl;
    var title = 'title' in options ? options['title'] : PWM_MAIN.showString('Title_Upload');

    uploadUrl = PWM_MAIN.addPwmFormIDtoURL(uploadUrl);

    var nextFunction = 'nextFunction' in options ? options['nextFunction'] : function(data){
        PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Success"), text: data['successMessage'],okAction:function(){
                PWM_MAIN.gotoUrl(currentUrl)
            }});
    };


    var completeFunction = function(data){
        console.log('upload dialog completeFunction() starting');
        if (data['error'] === true) {
            var errorText = PWM_MAIN.showString('Notice_UploadFailure');
            PWM_MAIN.showErrorDialog(data,{text:errorText,okAction:function(){
                    location.reload();
                }});
        } else {
            nextFunction(data);
        }
    };

    var errorFunction = function(status,statusText) {
        PWM_MAIN.closeWaitDialog();
        var errorText = PWM_MAIN.showString('Notice_UploadFailure');
        errorText += '<br/><br/>Status: ' + status;
        errorText += '<br/><br/>' + statusText;
        PWM_MAIN.showErrorDialog('',{text:errorText});
        //PWM_MAIN.showErrorDialog(errorText);
    };

    var progressFunction = function(data) {
        if (data.lengthComputable) {
            var decimal = data.loaded / data.total;
            console.log('upload progress: ' + decimal);
            require(["dijit/registry"],function(registry){
                var progressBar = registry.byId('progressBar');
                if (progressBar) {
                    progressBar.set("maximum", 100);
                    progressBar.set("indeterminate", false);
                    progressBar.set("value", decimal * 100);
                }
                var html5Bar = PWM_MAIN.getObject("wait");
                if (html5Bar) {
                    html5Bar.setAttribute("max", 100);
                    html5Bar.setAttribute("value", decimal * 100);
                }
            });
        } else {
            console.log('progressFunction: no data');
            return;
        }
    };

    var uploadFunction = function() {
        var files = PWM_MAIN.getObject('uploadFile').files;
        if (!files[0]) {
            alert('File is not selected.');
            return;
        }

        if ('urlUpdateFunction' in options) {
            uploadUrl = options['urlUpdateFunction'](uploadUrl);
        }

        var xhr = new XMLHttpRequest();
        var fd = new FormData();
        xhr.onreadystatechange = function() {
            console.log('upload handler onreadystate change: ' + xhr.readyState);
            if (xhr.readyState === 4) {
                xhr.upload.onprogress = null;
                if( xhr.status === 200) {
                    // Every thing ok, file uploaded
                    console.log(xhr.responseText); // handle response.
                    try {
                        var response = JSON.parse(xhr.response);
                        setTimeout(function(){
                            completeFunction(response);
                        },1000);
                    } catch (e) {
                        console.log('error parsing upload response log: ' + e)
                    }
                } else {
                    errorFunction(xhr.status, xhr.statusText)
                }
            }
        };

        xhr.upload.addEventListener('progress',progressFunction,false);
        xhr.upload.onprogress = progressFunction;
        xhr.open("POST", uploadUrl, true);
        xhr.setRequestHeader('Accept',"application/json");
        fd.append("fileUpload", files[0]);
        xhr.send(fd);
        PWM_GLOBAL['inhibitHealthUpdate'] = true;
        PWM_MAIN.IdleTimeoutHandler.cancelCountDownTimer();
        PWM_MAIN.showWaitDialog({title:PWM_MAIN.showString('Display_Uploading')});
    };

    completeFunction = 'completeFunction' in options ? options['completeFunction'] : completeFunction;

    var supportAjaxUploadWithProgress = function() {
        var supportFileAPI = function () {
            var fi = document.createElement('INPUT');
            fi.type = 'file';
            return 'files' in fi;
        };

        var supportAjaxUploadProgressEvents = function() {
            var xhr = new XMLHttpRequest();
            return !! (xhr && ('upload' in xhr) && ('onprogress' in xhr.upload));
        };

        var supportFormData = function() {
            return !! window.FormData;
        };

        return supportFileAPI() && supportAjaxUploadProgressEvents() && supportFormData();
    };

    if(!supportAjaxUploadWithProgress()){
        PWM_MAIN.showDialog('This browser does not support HTML5 file uploads.');
        return;
    }

    PWM_MAIN.showDialog({
        title:title,
        showClose:true,
        showOk:false,
        text:body,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('uploadButton','click',uploadFunction);
            PWM_MAIN.addEventHandler('uploadFile','change',function(){
                var btn = PWM_MAIN.getObject('uploadButton');
                console.log('value=' + btn.value);
                btn.disabled = btn.value ? true : false;
            });

        }
    });
};


UILibrary.passwordDialogPopup = function(options, state) {
    options = options === undefined ? {} : options;
    state = state === undefined ? {} : state;

    var option_title = 'title' in options ? options['title'] : 'Set Password';
    var option_writeFunction = 'writeFunction' in options ? options['writeFunction'] : function() {alert('No Password Write Function')};
    var option_minLength = 'minimumLength' in options ? options['minimumLength'] : 1;
    var option_showRandomGenerator = 'showRandomGenerator' in options ? options['showRandomGenerator'] : false;
    var option_showValues = 'showValues' in options ? options['showValues'] : false;
    var option_randomLength = 'randomLength' in options ? options['randomLength'] : 25;
    option_randomLength = option_randomLength < option_minLength ? option_minLength : option_randomLength;

    state['p1'] = 'p1' in state ? state['p1'] : '';
    state['p2'] = 'p2' in state ? state['p2'] : '';
    state['randomLength'] = 'randomLength' in state ? state['randomLength'] : option_randomLength;

    var markConfirmationCheckFunction = function(matchStatus) {
        if (matchStatus === "MATCH") {
            PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'visible';
            PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCheckMark").width = '15';
            PWM_MAIN.getObject("confirmCrossMark").width = '0';
        } else if (matchStatus === "NO_MATCH") {
            PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'visible';
            PWM_MAIN.getObject("confirmCheckMark").width = '0';
            PWM_MAIN.getObject("confirmCrossMark").width = '15';
        } else {
            PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCheckMark").width = '0';
            PWM_MAIN.getObject("confirmCrossMark").width = '0';
        }
    };

    var generateRandomFunction = function() {
        var length = state['randomLength'];
        var special = state['showSpecial'];

        if (!state['showFields']) {
            state['showFields'] = true;
        }

        var charMap = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
        if (special) {
            charMap += '~`!@#$%^&*()_-+=;:,.[]{}';
        }
        var postData = { };
        postData.maxLength = length;
        postData.minLength = length;
        postData.chars = charMap;
        postData.noUser = true;
        PWM_MAIN.getObject('button-storePassword').disabled = true;

        var url = PWM_GLOBAL['url-restservice'] + "/randompassword";
        var loadFunction = function(data) {
            state['p1'] = data['data']['password'];
            state['p2'] = '';
            UILibrary.passwordDialogPopup(options,state);
        };

        PWM_MAIN.showWaitDialog({loadFunction:function(){
                PWM_MAIN.ajaxRequest(url,loadFunction,{content:postData});
            }});
    };


    var validateFunction = function() {
        var password1 = state['p1'];
        var password2 = state['p2'];

        var matchStatus = "";

        PWM_MAIN.getObject('field-password-length').innerHTML = password1.length;
        PWM_MAIN.getObject('button-storePassword').disabled = true;

        if (option_minLength > 1 && password1.length < option_minLength) {
            PWM_MAIN.addCssClass('field-password-length','invalid-value');
        } else {
            PWM_MAIN.removeCssClass('field-password-length','invalid-value');
            if (password2.length > 0) {
                if (password1 === password2) {
                    matchStatus = "MATCH";
                    PWM_MAIN.getObject('button-storePassword').disabled = false;
                } else {
                    matchStatus = "NO_MATCH";
                }
            }
        }

        markConfirmationCheckFunction(matchStatus);
    };

    var bodyText = '';
    if (option_minLength > 1) {
        bodyText += 'Minimum Length: ' + option_minLength + '</span><br/><br/>'
    }
    bodyText += '<table class="noborder">'
        + '<tr><td><span class="formFieldLabel">' + PWM_MAIN.showString('Field_NewPassword') + '</span></td></tr>'
        + '<tr><td>';

    if (state['showFields']) {
        bodyText += '<textarea name="password1" id="password1" class="configStringInput" style="width: 400px; max-width: 400px; max-height:100px; overflow-y: auto" autocomplete="off">' + state['p1'] + '</textarea>';
    } else {
        bodyText += '<input name="password1" id="password1" class="configStringInput" type="password" style="width: 400px;" autocomplete="off" value="' + state['p1'] + '"></input>';
    }

    bodyText += '</td></tr>'
        + '<tr><td><span class="formFieldLabel">' + PWM_MAIN.showString('Field_ConfirmPassword') + '</span></td></tr>'
        + '<tr><td>';

    if (state['showFields']) {
        bodyText += '<textarea name="password2" id="password2" class="configStringInput" style="width: 400px; max-width: 400px; max-height:100px; overflow-y: auto" autocomplete="off">' + state['p2'] + '</textarea>';
    } else {
        bodyText += '<input name="password2" type="password" id="password2" class="configStringInput" style="width: 400px;" autocomplete="off" value="' + state['p2'] + '"></input>';
    }

    bodyText += '</td>'
        + '<td><div style="margin:0;">'
        + '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">'
        + '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">'
        + '</div></td>'
        + '</tr></table>';

    bodyText += '<br/>Length: <span id="field-password-length">-</span><br/><br/>';

    if (option_showRandomGenerator) {
        bodyText += '<div class="dialogSection" style="width: 400px"><span class="formFieldLabel">Generate Random Password </span><br/>'
            + '<label class="checkboxWrapper"><input id="input-special" type="checkbox"' + (state['showSpecial'] ? ' checked' : '') + '>Specials</input></label>'
            + '&nbsp;&nbsp;&nbsp;&nbsp;<input id="input-randomLength" type="number" min="10" max="1000" value="' + state['randomLength'] + '" style="width:45px">Length'
            + '&nbsp;&nbsp;&nbsp;&nbsp;<button id="button-generateRandom" name="button-generateRandom"><span class="pwm-icon pwm-icon-random btn-icon"></span>Generate Random</button>'
            + '</div><br/><br/>';
    }


    bodyText += '<button name="button-storePassword" class="btn" id="button-storePassword" disabled="true"/>'
        + '<span class="pwm-icon pwm-icon-forward btn-icon"></span>Store Password</button>';

    if (option_showValues) {
        bodyText += '&nbsp;&nbsp;'
            + '<label class="checkboxWrapper"><input id="show" type="checkbox"' + (state['showFields'] ? ' checked' : '') + '>Show Passwords</input></label>'
            + '</div><br/><br/>';
    }

    PWM_MAIN.showDialog({
        title: option_title,
        text: bodyText,
        showOk: false,
        showClose: true,
        loadFunction:function(){
            ShowHidePasswordHandler.init('password1');
            ShowHidePasswordHandler.init('password2');

            PWM_MAIN.addEventHandler('button-storePassword','click',function() {
                var passwordValue = PWM_MAIN.getObject('password1').value;
                PWM_MAIN.closeWaitDialog();
                option_writeFunction(passwordValue);
            });
            PWM_MAIN.addEventHandler('button-generateRandom','click',function() {
                generateRandomFunction();
            });
            PWM_MAIN.addEventHandler('password1','input',function(){
                state['p1'] = PWM_MAIN.getObject('password1').value;
                PWM_MAIN.getObject('password2').value = '';
                validateFunction();
            });
            PWM_MAIN.addEventHandler('password2','input',function(){
                state['p2'] = PWM_MAIN.getObject('password2').value;
                validateFunction();
            });
            PWM_MAIN.addEventHandler('show','change',function(){
                state['showFields'] = PWM_MAIN.getObject('show').checked;
                UILibrary.passwordDialogPopup(options, state);
            });
            PWM_MAIN.addEventHandler('input-special','change',function(){
                state['showSpecial'] = PWM_MAIN.getObject('input-special').checked;
                UILibrary.passwordDialogPopup(options, state);
            });
            PWM_MAIN.addEventHandler('input-randomLength','change',function(){
                state['randomLength'] = PWM_MAIN.getObject('input-randomLength').value;
            });
            PWM_MAIN.getObject('password1').focus();
            validateFunction();
        }
    });

};

UILibrary.displayElementsToTableContents = function(fields) {
    var htmlTable = '';
    for (var field in fields) {(function(field){
        var fieldData = fields[field];
        var classValue = fieldData['type'] === 'timestamp' ? 'timestamp' : '';
        htmlTable += '<tr><td>' + fieldData['label'] + '</td><td><span class="' + classValue + '" id="report_status_' + fieldData['key']  + '"</tr>';
    }(field)); }
    return htmlTable;
};

UILibrary.initElementsToTableContents = function(fields) {
    for (var field in fields) {(function(field) {
        var fieldData = fields[field];
        var value = fieldData['value'];
        if (fieldData['type'] === 'number') {
            value = PWM_MAIN.numberFormat(value);
        }
        PWM_MAIN.getObject('report_status_' + fieldData['key']).innerHTML = value;
        if (fieldData['type'] === 'timestamp') {
            PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject("report_status_" + fieldData['key']));
        }
    }(field)); }
};

