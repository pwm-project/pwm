/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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


var ActionHandler = {};
ActionHandler.defaultValue = {
    name:"",
    description:"",
    webActions:[],
    ldapActions:[]
};

ActionHandler.defaultWebValue = {
    method:"get",
    headers:{},
    url:"",
    body:"",
    successStatus:[200],
    username:"",
    password:""
};

ActionHandler.defaultLdapValue = {
    ldapMethod:"replace",
    attributeName:"",
    attributeValue:""
};

ActionHandler.httpMethodOptions = [
    { label: "Delete", value: "delete" },
    { label: "Get", value: "get" },
    { label: "Post", value: "post" },
    { label: "Put", value: "put" },
    { label: "Patch", value: "patch" }
];
ActionHandler.ldapMethodOptions = [
    { label: "Replace (Remove all existing values)", value: "replace" },
    { label: "Add (Append new value)", value: "add" },
    { label: "Remove (Remove specified value)", value: "remove" }
];

ActionHandler.init = function(keyName, postInitFunction) {
    console.log('ActionHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        ActionHandler.redraw(keyName);
        if ( postInitFunction ) {
            postInitFunction();
        }
    });
};

ActionHandler.redraw = function(keyName) {
    console.log('ActionHandler redraw for ' + keyName);
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var html = '';
    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        html += '<table class="noborder">';
        html += '<tr><td>Name</td><td></td><td>Description</td></tr>';

        for (var i in resultValue) {
            html += ActionHandler.drawRow(keyName, i, resultValue[i]);
        }

        html += '</table>';
    }

    html += '<br/><button class="btn" id="button-' + keyName + '-addValue"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Action</button>';
    parentDivElement.innerHTML = html;

    for (var i in resultValue) {
        html += ActionHandler.addRowHandlers(keyName, i, resultValue[i]);
    }

    PWM_MAIN.addEventHandler('button-' + keyName + '-addValue','click',function(){
        ActionHandler.addRow(keyName);
    });
};

ActionHandler.drawRow = function(settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";
    var optionList = ["webservice","ldap"];

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    var htmlRow = '<tr>';
    htmlRow += '<td style="background: #f6f9f8; border:1px solid #dae1e1; width:160px">';
    htmlRow += '<div class="noWrapTextBox" id="display-' + inputID + '-name" ></div>';
    htmlRow += '<td style="width:1px" id="icon-editDescription-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-edit"></span></td>';
    htmlRow += '</td><td style="background: #f6f9f8; border:1px solid #dae1e1; width:140px" id="border-editDescription-' + inputID + '">';
    htmlRow += '<div class="noWrapTextBox" id="display-' + inputID + '-description"></div>';
    htmlRow += '</td><td>';
    htmlRow += '<button id="button-' + inputID + '-options"><span class="btn-icon pwm-icon pwm-icon-sliders"/> Actions</button>';
    htmlRow += '</td>';
    htmlRow += '<td><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-' + inputID + '-deleteRow"></span></td>';
    htmlRow += '</tr>';
    return htmlRow;
};

ActionHandler.addRowHandlers = function(settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";
    UILibrary.addTextValueToElement('display-' + inputID + '-name',value['name']);
    UILibrary.addTextValueToElement('display-' + inputID + '-description',value['description']);

    PWM_MAIN.addEventHandler('button-' + inputID + '-options','click',function(){
        ActionHandler.showActionsDialog(settingKey, iteration);
    });

    var descriptionEditFunction = function() {
        UILibrary.stringEditorDialog({
            value: value['description'],
            textarea: true,
            title: 'Edit Description',
            completeFunction: function (newValue) {
                PWM_VAR['clientSettingCache'][settingKey][iteration]['description'] = newValue;
                ActionHandler.write(settingKey,function(){
                    ActionHandler.init(settingKey);
                });
            }
        });
    };

    PWM_MAIN.addEventHandler('icon-editDescription-' + inputID,'click',function(){
        descriptionEditFunction();
    });
    PWM_MAIN.addEventHandler('border-editDescription-' + inputID,'click',function(){
        descriptionEditFunction();
    });
    PWM_MAIN.addEventHandler('display-' + inputID + '-description','click',function(){
        descriptionEditFunction();
    });
    PWM_MAIN.addEventHandler('button-' + inputID + '-deleteRow','click',function(){
        ActionHandler.removeRow(settingKey, iteration);
    });
};

ActionHandler.write = function(settingKey, finishFunction) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

ActionHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            delete PWM_VAR['clientSettingCache'][keyName][iteration];
            console.log("removed iteration " + iteration + " from " + keyName + ", cached keyValue=" + PWM_VAR['clientSettingCache'][keyName]);
            ActionHandler.write(keyName,function(){
                ActionHandler.init(keyName);
            });
        }
    })
};

ActionHandler.addRow = function(keyName) {
    UILibrary.stringEditorDialog({
        title:'New Action',
        regex:'^[0-9a-zA-Z]+$',
        instructions: 'Please enter a descriptive name for the action.',
        placeholder:'Name',
        completeFunction:function(newName){
            let value = PWM_VAR['clientSettingCache'][keyName];
            let currentSize = PWM_MAIN.JSLibrary.itemCount(value);
            value[currentSize] = ActionHandler.defaultValue;
            value[currentSize].name = newName;
            ActionHandler.write(keyName,function(){
                ActionHandler.init(keyName, function(){
                    ActionHandler.showActionsDialog(keyName, currentSize);
                });
            });
        }
    });
};

ActionHandler.showActionsDialog = function(keyName, iteration) {
    var value = PWM_VAR['clientSettingCache'][keyName][iteration];
    var titleText = value['name'] + ' actions';
    var bodyText = '<table class="noborder">';

    if (!PWM_MAIN.JSLibrary.isEmpty(value['ldapActions'])) {
        bodyText += '<tr><td></td><td></td><td>LDAP Attribute</td></tr>';
    }
    for (var iter in value['ldapActions']) {
        (function (ldapActionsIter) {
            var inputID = keyName + '_' + iteration + "_ldapActions_"  + ldapActionsIter;
            bodyText += '<tr id="tableRow-' + inputID + '">'
                + '<td style="width:1px" id="icon-editLdapAction-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-edit"></span></td>'
                + '<td>LDAP Action</td>'
                + '<td><input disabled id="value-' + inputID + '" class="configStringInput" style="max-width: 200px"></td>'
                + '<td><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-deleteRow-' + inputID + '"></span></td>'
                + '</tr>';
        }(iter));
    }
    bodyText += '<tr><td>&nbsp;</td></tr>';

    if (!PWM_MAIN.JSLibrary.isEmpty(value['webActions'])) {
        bodyText += '<tr><td></td><td></td><td>URL</td></tr>';
    }
    for (var iter in value['webActions']) {
        (function (webActionIter) {
            var inputID = keyName + '_' + iteration + "_webActions_"  + webActionIter;
            bodyText += '<tr id="tableRow-' + inputID + '">'
                + '<td style="width:1px" id="icon-editWebAction-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-edit"></span></td>'
                + '<td>Web Service Action</td>'
                + '<td><input disabled id="value-' + inputID + '" class="configStringInput" style="max-width: 200px"></td>'
                + '<td><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-deleteRow-' + inputID + '"></span></td>'
                + '</tr>';
        }(iter));
    }

    bodyText += '</table><br/>';

    var inputID = keyName + '_' + iteration + "_";
    bodyText += '<br/><button class="btn" id="button-addLdap-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add LDAP Action</button>';
    bodyText += '<br/><button class="btn" id="button-addWebService-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Web Service Action</button>';

    PWM_MAIN.showDialog({
        title: titleText,
        text: bodyText,
        loadFunction: function(){
            for (var iter in value['ldapActions']) {
                (function (ldapActionsIter) {
                    var inputID = keyName + '_' + iteration + "_ldapActions_"  + ldapActionsIter;
                    PWM_MAIN.addEventHandler('icon-editLdapAction-' + inputID ,'click',function(){
                        ActionHandler.addOrEditLdapAction(keyName,iteration,ldapActionsIter);
                    });
                    PWM_MAIN.addEventHandler('button-deleteRow-' + inputID ,'click',function(){
                        PWM_MAIN.showConfirmDialog({okAction:function () {
                                PWM_VAR['clientSettingCache'][keyName][iteration]['ldapActions'].splice(ldapActionsIter,1);
                                ActionHandler.write(keyName,function(){
                                    ActionHandler.showActionsDialog(keyName,iteration);
                                });
                            }, cancelAction:function(){
                                ActionHandler.showActionsDialog(keyName,iteration);
                            }
                        });
                    });
                    var value = PWM_VAR['clientSettingCache'][keyName][iteration]['ldapActions'][ldapActionsIter];
                    PWM_MAIN.getObject('value-' + inputID).value =  value['attributeName'];
                }(iter));
            }
            for (var iter in value['webActions']) {
                (function (webActionIter) {
                    var inputID = keyName + '_' + iteration + "_webActions_"  + webActionIter;
                    PWM_MAIN.addEventHandler('icon-editWebAction-' + inputID ,'click',function(){
                        ActionHandler.addOrEditWebAction(keyName,iteration,webActionIter);
                    });
                    PWM_MAIN.addEventHandler('button-deleteRow-' + inputID ,'click',function(){
                        PWM_MAIN.showConfirmDialog({okAction:function () {
                                PWM_VAR['clientSettingCache'][keyName][iteration]['webActions'].splice(webActionIter,1);
                                ActionHandler.write(keyName,function(){
                                    ActionHandler.showActionsDialog(keyName,iteration);
                                });
                            }, cancelAction:function(){
                                ActionHandler.showActionsDialog(keyName,iteration);
                            }
                        });
                    });
                    var value = PWM_VAR['clientSettingCache'][keyName][iteration]['webActions'][webActionIter];
                    PWM_MAIN.getObject('value-' + inputID).value =  value['url'];
                }(iter));
            }

            inputID = keyName + '_' + iteration + "_";
            PWM_MAIN.addEventHandler('button-addLdap-' + inputID,'click',function(){
                UILibrary.stringEditorDialog({
                    textarea: false,
                    title: 'Attribute Name',
                    completeFunction: function (newValue) {
                        var currentSize = PWM_MAIN.JSLibrary.itemCount(value['ldapActions']);
                        value['ldapActions'].push(JSON.parse(JSON.stringify(ActionHandler.defaultLdapValue)));
                        value['ldapActions'][currentSize]['attributeName'] = newValue;
                        ActionHandler.write(keyName,function(){
                            ActionHandler.addOrEditLdapAction(keyName,iteration,currentSize);
                        });
                    }
                });
            });
            PWM_MAIN.addEventHandler('button-addWebService-' + inputID,'click',function(){
                UILibrary.stringEditorDialog({
                    textarea: false,
                    title: 'URL',
                    completeFunction: function (newValue) {
                        var currentSize = PWM_MAIN.JSLibrary.itemCount(value['webActions']);
                        value['webActions'].push(JSON.parse(JSON.stringify(ActionHandler.defaultWebValue)));
                        value['webActions'][currentSize]['url'] = newValue;
                        ActionHandler.write(keyName,function(){
                            ActionHandler.addOrEditWebAction(keyName,iteration,currentSize);
                        });
                    }
                });
            });
        }
    });
};

ActionHandler.addOrEditLdapAction = function(keyName, iteration, ldapActionIter) {
    var inputID = 'value_' + keyName + '_' + iteration + "_" + ldapActionIter;
    var value = PWM_VAR['clientSettingCache'][keyName][iteration]['ldapActions'][ldapActionIter];
    var titleText = 'LDAP options';

    var bodyText = '<table class="noborder">';
    bodyText += '<tr>';
    bodyText += '<td class="key">Attribute Name</td><td><input style="width:300px" class="configStringInput" type="text" id="input-' + inputID + '-attributeName' + '" value="' + value['attributeName'] + '"/></td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">Attribute Value</td><td><input style="width:300px" class="configStringInput" type="text" id="input-' + inputID + '-attributeValue' + '" value="' + value['attributeValue'] + '"/></td>';
    bodyText += '</tr>';
    bodyText += '<tr>';
    bodyText += '<td class="key">Operation Type</td><td class="noborder"><select id="select-' + inputID + '-ldapMethod' + '">';

    for (var optionItem in ActionHandler.ldapMethodOptions) {
        var label = ActionHandler.ldapMethodOptions[optionItem]['label'];
        var optionValue = ActionHandler.ldapMethodOptions[optionItem]['value'];
        var selected = optionValue === value['ldapMethod'];
        bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
    }
    bodyText += '</td></tr>';
    bodyText += '</table>';

    PWM_MAIN.showDialog({
        title: titleText,
        text: bodyText,
        okAction: function(){
            ActionHandler.showActionsDialog(keyName, iteration);
        },
        loadFunction: function(){
            PWM_MAIN.addEventHandler('input-' + inputID + '-attributeName','input',function(){
                value['attributeName'] = PWM_MAIN.getObject('input-' + inputID + '-attributeName').value;
                ActionHandler.write(keyName);
            });
            PWM_MAIN.addEventHandler('input-' + inputID + '-attributeValue','input',function(){
                value['attributeValue'] = PWM_MAIN.getObject('input-' + inputID + '-attributeValue').value;
                ActionHandler.write(keyName);
            });
            PWM_MAIN.addEventHandler('select-' + inputID + '-ldapMethod','change',function(){
                value['ldapMethod'] = PWM_MAIN.getObject('select-' + inputID + '-ldapMethod').value;
                ActionHandler.write(keyName);
            });
        }
    });
};


ActionHandler.addOrEditWebAction = function(keyName, iteration, webActionIter) {
    var inputID = 'value_' + keyName + '_' + iteration + "_" + webActionIter;
    var value = PWM_VAR['clientSettingCache'][keyName][iteration]['webActions'][webActionIter];
    var titleText = 'Web Service Options';
    var showBody = value['method'] !== 'get' && value['method'] !== 'delete';

    var bodyText = '<table class="noborder">';
    bodyText += '<tr>';
    bodyText += '<td class="key">HTTP Method</td><td class="noborder" ><select id="select-' + inputID + '-method">';

    for (var optionItem in ActionHandler.httpMethodOptions) {
        var label = ActionHandler.httpMethodOptions[optionItem]['label'];
        var optionValue = ActionHandler.httpMethodOptions[optionItem]['value'];
        var selected = optionValue === value['method'];
        bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
    }
    bodyText += '</td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">HTTP Headers</td><td><button id="button-' + inputID + '-headers"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button></td>';
    bodyText += '</tr><tr>';
    bodyText += '<td class="key">URL</td><td><input type="text" class="configstringinput" style="width:400px" placeholder="http://www.example.com/service" disabled id="input-' + inputID + '-url' + '"/></td>';
    bodyText += '</tr>';
    bodyText += '<td class="key">Basic Auth Username</td><td><input type="text" class="configstringinput" style="width:350px" placeholder="Username" disabled id="input-' + inputID + '-username' + '"/></td>';
    bodyText += '</tr>';
    bodyText += '<td class="key">Basic Auth Password</td><td><input type="password" class="configstringinput" style="width:350px" disabled id="input-' + inputID + '-password' + '"/></td>';
    bodyText += '</tr>';
    if (showBody) {
        bodyText += '<tr><td class="key">Body</td><td><textarea style="max-width:400px; height:100px; max-height:100px" class="configStringInput" disabled id="input-' + inputID + '-body' + '"/></textarea></td></tr>';
    }
    bodyText += '<td class="key">Success Status Codes</td><td><input type="text" class="configstringinput" style="width:300px" disabled id="input-' + inputID + '-successStatus' + '"/>';
    bodyText += '<button style="margin-left: 15px" id="button-' + inputID + '-successStatus"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button>';
    bodyText += '</td></tr>';
    if (!PWM_MAIN.JSLibrary.isEmpty(value['certificateInfos'])) {
        bodyText += '<tr><td class="key">Certificates</td><td><a id="button-' + inputID + '-certDetail">View Certificates</a></td>';
        bodyText += '</tr>';
    } else {
        bodyText += '<tr><td class="key">Certificates</td><td>None</td>';
        bodyText += '</tr>';
    }
    bodyText += '';
    bodyText += '</table>';

    if (!PWM_MAIN.JSLibrary.isEmpty(value['certificateInfos'])) {
        bodyText += '<button class="btn" id="button-' + inputID + '-clearCertificates"><span class="btn-icon pwm-icon pwm-icon-trash"></span>Clear Certificates</button>'
    } else {
        bodyText += '<button class="btn" id="button-' + inputID + '-importCertificates"><span class="btn-icon pwm-icon pwm-icon-download"></span>Import Certificates</button>'
    }

    PWM_MAIN.showDialog({
        title: titleText,
        text: bodyText,
        okAction: function(){
            ActionHandler.showActionsDialog(keyName, iteration);
        },
        loadFunction: function(){
            PWM_MAIN.addEventHandler('button-' + inputID + '-headers','click',function(){
                ActionHandler.showHeadersDialog(keyName,iteration, webActionIter);
            });
            PWM_MAIN.addEventHandler('select-' + inputID + '-method','change',function(){
                var methodValue = PWM_MAIN.getObject('select-' + inputID + '-method').value;
                if (methodValue === 'get') {
                    value['body'] = '';
                }
                value['method'] = methodValue;
                ActionHandler.write(keyName, function(){ ActionHandler.addOrEditWebAction(keyName,iteration,webActionIter)});
            });

            PWM_MAIN.getObject('input-' + inputID + '-url').value = value['url'];
            PWM_MAIN.getObject('input-' + inputID + '-url').disabled = false;
            PWM_MAIN.addEventHandler('input-' + inputID + '-url','input',function(){
                value['url'] = PWM_MAIN.getObject('input-' + inputID + '-url').value;
                ActionHandler.write(keyName);
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

            PWM_MAIN.getObject('input-' + inputID + '-successStatus').value = value['successStatus'] ? value['successStatus'].join() : '';
            PWM_MAIN.addEventHandler('button-' + inputID + '-successStatus', 'click', function(){
                var options = {};
                options['regex'] = '[0-9]{3}';
                options['title'] = 'Success Status Codes';
                options['instructions'] = 'Enter the three digit HTTP status codes that will be considered a success if returned by the remote web service.';
                options['completeFunction'] = function(values){
                    values.sort();
                    value['successStatus'] = values;
                    ActionHandler.write(keyName);
                    ActionHandler.addOrEditWebAction(keyName, iteration, webActionIter)
                };
                var values = 'successStatus' in value ? value['successStatus'] : [];
                values.sort();
                options['value'] = values;
                UILibrary.stringArrayEditorDialog(options);
            });

            if (showBody) {
                UILibrary.addTextValueToElement('input-' + inputID + '-body', value['body']);
                PWM_MAIN.getObject('input-' + inputID + '-body').disabled = false;
                PWM_MAIN.addEventHandler('input-' + inputID + '-body', 'input', function () {
                    value['body'] = PWM_MAIN.getObject('input-' + inputID + '-body').value;
                    ActionHandler.write(keyName);
                });
            }
            if (!PWM_MAIN.JSLibrary.isEmpty(value['certificateInfos'])) {
                PWM_MAIN.addEventHandler('button-' + inputID + '-certDetail','click',function(){
                    var bodyText = '';
                    for (var i in value['certificateInfos']) {
                        var certificate = value['certificateInfos'][i];
                        bodyText += X509CertificateHandler.certificateToHtml(certificate,keyName,i);
                    }
                    var cancelFunction = function(){ ActionHandler.addOrEditWebAction(keyName,iteration,webActionIter); };
                    var loadFunction = function(){
                        for (var i in value['certificateInfos']) {
                            var certificate = value['certificateInfos'][i];
                            X509CertificateHandler.certHtmlActions(certificate,keyName,i);
                        }
                    };
                    PWM_MAIN.showDialog({
                        title:'Certificate Detail',
                        dialogClass: 'wide',
                        text:bodyText,
                        okAction:cancelFunction,
                        loadFunction:loadFunction
                    });
                });
                PWM_MAIN.addEventHandler('button-' + inputID + '-clearCertificates','click',function() {
                    PWM_MAIN.showConfirmDialog({okAction:function(){
                            delete value['certificates'];
                            delete value['certificateInfos'];
                            ActionHandler.write(keyName, function(){ ActionHandler.addOrEditWebAction(keyName,iteration,webActionIter)});
                        },cancelAction:function(){
                            ActionHandler.showActionsDialog(keyName,iteration);
                        }});
                });
            } else {
                PWM_MAIN.addEventHandler('button-' + inputID + '-importCertificates','click',function() {
                    var dataHandler = function(data) {
                        var msgBody = '<div style="max-height: 400px; overflow-y: auto">' + data['successMessage'] + '</div>';
                        PWM_MAIN.showDialog({width:700,title: 'Results', text: msgBody, okAction: function () {
                                PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
                                    PWM_VAR['clientSettingCache'][keyName] = resultValue;
                                    ActionHandler.addOrEditWebAction(keyName,iteration,webActionIter)
                                });
                            }});
                    };
                    var extraData = {};
                    extraData.iteration = iteration;
                    extraData.webActionIter = webActionIter;
                    PWM_CFGEDIT.executeSettingFunction(keyName, 'password.pwm.config.function.ActionCertImportFunction', dataHandler, JSON.stringify(extraData));
                });
            }

        }
    });
};

ActionHandler.showHeadersDialog = function(keyName, iteration, webActionIter) {
    var settingValue = PWM_VAR['clientSettingCache'][keyName][iteration]['webActions'][webActionIter];

    var inputID = 'value_' + keyName + '_' + iteration + "_webAction_" + webActionIter +  "_headers_";

    var bodyText = '';
    bodyText += '<table class="noborder">';
    bodyText += '<tr><td><b>Name</b></td><td><b>Value</b></td></tr>';
    for (var iter in settingValue['headers']) {
        (function(headerName) {
            var value = settingValue['headers'][headerName];
            var optionID = inputID + headerName;
            bodyText += '<tr><td class="border">' + headerName + '</td><td class="border">' + value + '</td>';
            bodyText += '<td style="width:15px;"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-' + optionID + '-deleteRow"></span></td>';
            bodyText += '</tr>';
        }(iter));
    }
    bodyText += '</table>';

    PWM_MAIN.showDialog({
        title: 'HTTP Headers',
        text: bodyText,
        buttonHtml:'<button id="button-' + inputID + '-addHeader" class="btn"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Header</button>',
        okAction: function() {
            ActionHandler.addOrEditWebAction(keyName,iteration,webActionIter);
        },
        loadFunction: function() {
            for (var iter in settingValue['headers']) {
                (function(headerName) {
                    var headerID = inputID + headerName;
                    PWM_MAIN.addEventHandler('button-' + headerID + '-deleteRow', 'click', function () {
                        delete settingValue['headers'][headerName];
                        ActionHandler.write(keyName);
                        ActionHandler.showHeadersDialog(keyName, iteration, webActionIter);
                    });
                }(iter));
            }
            PWM_MAIN.addEventHandler('button-' + inputID + '-addHeader','click',function(){
                ActionHandler.addHeader(keyName, iteration, webActionIter);
            });
        }
    });
};

ActionHandler.addHeader = function(keyName, iteration, webActionIter) {
    var body = '<table class="noborder">';
    body += '<tr><td>Name</td><td><input class="configStringInput" id="newHeaderName" style="width:300px"/></td></tr>';
    body += '<tr><td>Value</td><td><input class="configStringInput" id="newHeaderValue" style="width:300px"/></td></tr>';
    body += '</table>';

    var updateFunction = function(){
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
            var headers = PWM_VAR['clientSettingCache'][keyName][iteration]['webActions'][webActionIter]['headers'];
            headers[PWM_VAR['newHeaderName']] = PWM_VAR['newHeaderValue'];
            ActionHandler.write(keyName);
            ActionHandler.showHeadersDialog(keyName, iteration, webActionIter);
        },cancelAction:function(){
            ActionHandler.showHeadersDialog(keyName, iteration, webActionIter);
        }
    });

};
