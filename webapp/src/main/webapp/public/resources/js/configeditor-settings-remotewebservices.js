/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

var RemoteWebServiceHandler = {};
RemoteWebServiceHandler.defaultValue = {
    name:"",
    method:"get",
    url:"",
    body:"",
    username:"",
    password:"",
    headers:{}
};
RemoteWebServiceHandler.httpMethodOptions = [
    { label: "Delete", value: "delete" },
    { label: "Get", value: "get" },
    { label: "Post", value: "post" },
    { label: "Put", value: "put" },
    { label: "Patch", value: "patch" }
];

RemoteWebServiceHandler.init = function(keyName) {
    console.log('RemoteWebServiceHandler init for ' + keyName);
    const parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        RemoteWebServiceHandler.redraw(keyName);
    });
};

RemoteWebServiceHandler.redraw = function(keyName) {
    console.log('RemoteWebServiceHandler redraw for ' + keyName);
    const resultValue = PWM_VAR['clientSettingCache'][keyName];
    const parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    let html = '';
    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        html += '<table class="noborder">';
        html += '<tr><td>Name</td><td>URL</td></tr>';

        for (const loop in resultValue) {
            (function (loop) {
                html += RemoteWebServiceHandler.drawRow(keyName, loop, resultValue[loop]);
            })(loop);
        }

        html += '</table>';
    }

    const rowCount = PWM_MAIN.JSLibrary.itemCount(resultValue);
    const maxRowCount = PWM_SETTINGS['settings'][keyName]['properties']['Maximum'];
    if (maxRowCount > 0 && rowCount < maxRowCount) {
        html += '<br/><button class="btn" id="button-' + keyName + '-addValue"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Service</button>';
    }

    parentDivElement.innerHTML = html;

    for (const i in resultValue) {
        html += RemoteWebServiceHandler.addRowHandlers(keyName, i, resultValue[i]);
    }

    PWM_MAIN.addEventHandler('button-' + keyName + '-addValue','click',function(){
        RemoteWebServiceHandler.addRow(keyName);
    });
};

RemoteWebServiceHandler.drawRow = function(settingKey, iteration) {
    const inputID = 'value_' + settingKey + '_' + iteration + "_";

    const newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    let htmlRow = '<tr>';
    htmlRow += '<td class="border">';
    htmlRow += '<div class="noWrapTextBox" style="width:50px" id="display-' + inputID + '-name" ></div>';
    htmlRow += '</td><td>';
    htmlRow += '<div class="noWrapTextBox" style="width:250px" id="display-' + inputID + '-url" ></div>';
    htmlRow += '</td><td>';
    htmlRow += '<button id="button-' + inputID + '-options"><span class="btn-icon pwm-icon pwm-icon-sliders"/> Options</button>';
    htmlRow += '</td>';
    htmlRow += '<td><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-' + inputID + '-deleteRow"></span></td>';
    htmlRow += '</tr>';
    return htmlRow;
};

RemoteWebServiceHandler.addRowHandlers = function(settingKey, iteration, value) {
    const inputID = 'value_' + settingKey + '_' + iteration + "_";
    UILibrary.addTextValueToElement('display-' + inputID + '-name',value['name']);
    UILibrary.addTextValueToElement('display-' + inputID + '-url',value['url']);
    UILibrary.addTextValueToElement('display-' + inputID + '-description',value['description']);
    PWM_MAIN.addEventHandler('button-' + inputID + '-options','click',function(){
        RemoteWebServiceHandler.showOptionsDialog(settingKey, iteration);
    });

    PWM_MAIN.addEventHandler('button-' + inputID + '-deleteRow','click',function(){
        RemoteWebServiceHandler.removeRow(settingKey, iteration);
    });
};

RemoteWebServiceHandler.write = function(settingKey, finishFunction) {
    const cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

RemoteWebServiceHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            delete PWM_VAR['clientSettingCache'][keyName][iteration];
            console.log("removed iteration " + iteration + " from " + keyName + ", cached keyValue=" + PWM_VAR['clientSettingCache'][keyName]);
            RemoteWebServiceHandler.write(keyName,function(){
                RemoteWebServiceHandler.init(keyName);
            });
        }
    })
};

RemoteWebServiceHandler.addRow = function(keyName) {
    UILibrary.stringEditorDialog({
        title:'New Remote Web Service',
        regex:'^[0-9a-zA-Z]+$',
        instructions:'Please enter a descriptive name for the web service.',
        placeholder:'Name',
        completeFunction:function(value){
            const currentSize = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][keyName]);
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = RemoteWebServiceHandler.defaultValue;
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1].name = value;

            if (PWM_SETTINGS['settings'][keyName]['properties']['MethodType']) {
                PWM_VAR['clientSettingCache'][keyName][currentSize + 1].method = PWM_SETTINGS['settings'][keyName]['properties']['MethodType'];
            }

            RemoteWebServiceHandler.write(keyName,function(){
                RemoteWebServiceHandler.init(keyName);
            });

        }
    });
};

RemoteWebServiceHandler.showOptionsDialog = function(keyName, iteration) {
    const inputID = 'value_' + keyName + '_' + iteration + "_";
    const value = PWM_VAR['clientSettingCache'][keyName][iteration];
    const titleText = 'Web Service options for ' + value['name'];
    let bodyText = '<table class="noborder">';

    const hasMethodType = 'MethodType' in PWM_SETTINGS['settings'][keyName]['properties'];
    const showBody = value['method'] !== 'get' && !(PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'], 'WebService_NoBody'));

    bodyText += '<tr>';
    bodyText += '<td class="key">HTTP Method</td><td class="noborder" ><select id="select-' + inputID + '-method"'
        + (hasMethodType ? ' disabled' : '')
        + '>';

    for (const optionItem in RemoteWebServiceHandler.httpMethodOptions) {
        const label = RemoteWebServiceHandler.httpMethodOptions[optionItem]['label'];
        const optionValue = RemoteWebServiceHandler.httpMethodOptions[optionItem]['value'];
        const selected = optionValue === value['method'];
        bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
    }
    bodyText += '</td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">HTTP Headers</td><td><button id="button-' + inputID + '-headers"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button></td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">URL</td><td><input pattern="^(http|https)://.+$" type="text" class="configstringinput" style="width:400px" placeholder="http://www.example.com/service" disabled id="input-' + inputID + '-url' + '"/></td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">Basic Auth Username</td><td><input type="text" class="configstringinput" style="width:350px" placeholder="Username" disabled id="input-' + inputID + '-username' + '"/></td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">Basic Auth Password</td><td><input type="password" class="configstringinput" style="width:350px" disabled id="input-' + inputID + '-password' + '"/></td>';
    bodyText += '</tr>';
    if (showBody) {
        bodyText += '<tr><td class="key">Body</td><td><textarea style="max-width:400px; height:100px; max-height:100px" class="configStringInput" id="input-' + inputID + '-body' + '"/>' + value['body'] + '</textarea></td></tr>';
    }
    if (value['certificates']) {
        bodyText += '<tr><td class="key">Certificates</td><td><a id="button-' + inputID + '-certDetail">View Certificates</a></td>';
        bodyText += '</tr>';
    } else {
        bodyText += '<tr><td class="key">Certificates</td><td>None</td>';
        bodyText += '</tr>';
    }
    bodyText += '';

    bodyText += '</table>';

    if (value['certificates']) {
        bodyText += '<button class="btn" id="button-' + inputID + '-clearCertificates"><span class="btn-icon pwm-icon pwm-icon-trash"></span>Clear Certificates</button>'
    } else {
        bodyText += '<button class="btn" id="button-' + inputID + '-importCertificates"><span class="btn-icon pwm-icon pwm-icon-download"></span>Import Certificates</button>'
    }

    PWM_MAIN.showDialog({
        title: titleText,
        text: bodyText,
        okAction: function(){
            RemoteWebServiceHandler.init(keyName);
        },
        loadFunction: function(){
            PWM_MAIN.addEventHandler('button-' + inputID + '-headers','click',function(){
                RemoteWebServiceHandler.showHeadersDialog(keyName,iteration);
            });

            PWM_MAIN.addEventHandler('select-' + inputID + '-method','change',function(){
                const methodValue = PWM_MAIN.getObject('select-' + inputID + '-method').value;
                if (methodValue === 'get') {
                    value['body'] = '';
                }
                value['method'] = methodValue;
                RemoteWebServiceHandler.write(keyName, function(){ RemoteWebServiceHandler.showOptionsDialog(keyName,iteration)});
            });
            PWM_MAIN.getObject('input-' + inputID + '-url').value = value['url'] ? value['url'] : '';
            PWM_MAIN.getObject('input-' + inputID + '-url').disabled = false;
            PWM_MAIN.addEventHandler('input-' + inputID + '-url','input',function(){
                value['url'] = PWM_MAIN.getObject('input-' + inputID + '-url').value;
                RemoteWebServiceHandler.write(keyName);
            });

            PWM_MAIN.getObject('input-' + inputID + '-username').value = value['username'] ? value['username'] : '';
            PWM_MAIN.getObject('input-' + inputID + '-username').disabled = false;
            PWM_MAIN.addEventHandler('input-' + inputID + '-username','input',function(){
                value['username'] = PWM_MAIN.getObject('input-' + inputID + '-username').value;
                ActionHandler.write(keyName);
            });

            PWM_MAIN.getObject('input-' + inputID + '-password').value = value['password'] ? value['password'] : '';
            PWM_MAIN.getObject('input-' + inputID + '-password').disabled = false;
            PWM_MAIN.addEventHandler('input-' + inputID + '-password','input',function(){
                value['password'] = PWM_MAIN.getObject('input-' + inputID + '-password').value;
                ActionHandler.write(keyName);
            });

            PWM_MAIN.addEventHandler('input-' + inputID + '-body','input',function(){
                value['body'] = PWM_MAIN.getObject('input-' + inputID + '-body').value;
                RemoteWebServiceHandler.write(keyName);
            });
            if (value['certificates']) {
                PWM_MAIN.addEventHandler('button-' + inputID + '-certDetail','click',function(){
                    let extraData = JSON.stringify({iteration:iteration,keyName:keyName});
                    debugger;
                    PWM_CFGEDIT.executeSettingFunction(keyName, 'password.pwm.http.servlet.configeditor.function.RemoteWebServiceCertViewerFunction',
                        RemoteWebServiceHandler.showCertificateViewerDialog, extraData)

                });
                PWM_MAIN.addEventHandler('button-' + inputID + '-clearCertificates','click',function() {
                    PWM_MAIN.showConfirmDialog({okAction:function(){
                        delete value['certificates'];
                        delete value['certificateInfos'];
                        RemoteWebServiceHandler.write(keyName, function(){ RemoteWebServiceHandler.showOptionsDialog(keyName,iteration)});
                    },cancelAction:function(){
                        RemoteWebServiceHandler.showOptionsDialog(keyName,iteration);
                    }});
                });
            } else {
                PWM_MAIN.addEventHandler('button-' + inputID + '-importCertificates','click',function() {
                    const dataHandler = function(data) {
                        const msgBody = '<div style="max-height: 400px; overflow-y: auto">' + data['successMessage'] + '</div>';
                        PWM_MAIN.showDialog({width:700,title: 'Results', text: msgBody, okAction: function () {
                            PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
                                PWM_VAR['clientSettingCache'][keyName] = resultValue;
                                RemoteWebServiceHandler.showOptionsDialog(keyName, iteration);
                            });
                        }});
                    };
                    PWM_CFGEDIT.executeSettingFunction(keyName, 'password.pwm.http.servlet.configeditor.function.RemoteWebServiceCertImportFunction', dataHandler, value['name'])
                });
            }

        }
    });
};



RemoteWebServiceHandler.showHeadersDialog = function(keyName, iteration) {
    const settingValue = PWM_VAR['clientSettingCache'][keyName][iteration];
    const inputID = 'value_' + keyName + '_' + iteration + "_" + "headers_";

    let bodyText = '';
    bodyText += '<table class="noborder">';
    bodyText += '<tr><td><b>Name</b></td><td><b>Value</b></td></tr>';
    for (const iter in settingValue['headers']) {
        (function(headerName) {
            const value = settingValue['headers'][headerName];
            const optionID = inputID + headerName;
            bodyText += '<tr><td class="border">' + headerName + '</td><td class="border">' + value + '</td>';
            bodyText += '<td style="width:15px;"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-' + optionID + '-deleteRow"></span></td>';
            bodyText += '</tr>';
        }(iter));
    }
    bodyText += '</table>';

    PWM_MAIN.showDialog({
        title: 'HTTP Headers for webservice ' + settingValue['name'],
        text: bodyText,
        buttonHtml:'<button id="button-' + inputID + '-addHeader" class="btn"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Header</button>',
        okAction: function() {
            RemoteWebServiceHandler.showOptionsDialog(keyName,iteration);
        },
        loadFunction: function() {
            for (const iter in settingValue['headers']) {
                (function(headerName) {
                    const headerID = inputID + headerName;
                    PWM_MAIN.addEventHandler('button-' + headerID + '-deleteRow', 'click', function () {
                        delete settingValue['headers'][headerName];
                        RemoteWebServiceHandler.write(keyName);
                        RemoteWebServiceHandler.showHeadersDialog(keyName, iteration);
                    });
                }(iter));
            }
            PWM_MAIN.addEventHandler('button-' + inputID + '-addHeader','click',function(){
                RemoteWebServiceHandler.addHeader(keyName, iteration);
            });
        }
    });
};

RemoteWebServiceHandler.addHeader = function(keyName, iteration) {
    let body = '<table class="noborder">';
    body += '<tr><td>Name</td><td><input class="configStringInput" id="newHeaderName" style="width:300px"/></td></tr>';
    body += '<tr><td>Value</td><td><input class="configStringInput" id="newHeaderValue" style="width:300px"/></td></tr>';
    body += '</table>';

    const updateFunction = function(){
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        PWM_VAR['newHeaderName'] = PWM_MAIN.getObject('newHeaderName').value;
        PWM_VAR['newHeaderValue'] = PWM_MAIN.getObject('newHeaderValue').value;
        if (PWM_VAR['newHeaderName'].length > 0 && PWM_VAR['newHeaderValue'].length > 0) {
            PWM_MAIN.getObject('dialog_ok_button').disabled = false;
        }
    };

    PWM_MAIN.showConfirmDialog({
        title:'New Header',
        text:body,
        showClose:true,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('newHeaderName','input',function(){
                updateFunction();
            });
            PWM_MAIN.addEventHandler('newHeaderValue','input',function(){
                updateFunction();
            });
            updateFunction();
        },okAction:function(){
            const headers = PWM_VAR['clientSettingCache'][keyName][iteration]['headers'];
            headers[PWM_VAR['newHeaderName']] = PWM_VAR['newHeaderValue'];
            RemoteWebServiceHandler.write(keyName);
            RemoteWebServiceHandler.showHeadersDialog(keyName, iteration);
        },cancelAction:function(){
            RemoteWebServiceHandler.showHeadersDialog(keyName, iteration);
        }
    });
};

RemoteWebServiceHandler.showCertificateViewerDialog = function(data,extraDataJson) {
    let extraData = JSON.parse(extraDataJson)
    let keyName = extraData['keyName'];
    let certInfos = data['data'];
    let bodyText = '';
    for (let i in certInfos) {
        bodyText += X509CertificateHandler.certificateToHtml(certInfos[i],keyName,i);
    }
    let cancelFunction = function(){ RemoteWebServiceHandler.showOptionsDialog(keyName, extraData['iteration'])};
    let loadFunction = function(){
        for (let i in certInfos) {
            X509CertificateHandler.certHtmlActions(certInfos[i],keyName,i);
        }
    };
    PWM_MAIN.showDialog({
        title:'Certificate Detail',
        dialogClass: 'wide',
        text:bodyText,
        okAction:cancelFunction,
        loadFunction:loadFunction
    });
};
