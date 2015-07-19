/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

// -------------------------- common elements handler ------------------------------------

var UILibrary = {};
UILibrary.stringEditorDialog = function(options){
    options = options === undefined ? {} : options;
    var title = 'title' in options ? options['title'] : 'Edit Value';
    var completeFunction = 'completeFunction' in options ? options['completeFunction'] : function() {alert('no string editor dialog complete function')};
    var regexString = 'regex' in options && options['regex'] ? options['regex'] : '.+';
    var initialValue = 'value' in options ? options['value'] : '';
    var placeholder = 'placeholder' in options ? options['placeholder'] : '';
    var textarea = 'textarea' in options ? options['textarea'] : false;

    var regexObject = new RegExp(regexString);
    var text = '';
    text += '<div style="visibility: hidden;" id="panel-valueWarning"><span class="fa fa-warning message-error"></span>&nbsp;' + PWM_CONFIG.showString('Warning_ValueIncorrectFormat') + '</div>';
    text += '<br/>';

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
            var passedValidation = regexObject  != null && regexObject.test(value);

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

UILibrary.addTextValueToElement = function(elementID, input) {
    var element = PWM_MAIN.getObject(elementID);
    if (element) {
        element.innerHTML = '';
        element.appendChild(document.createTextNode(input));
    }
};

UILibrary.addAddLocaleButtonRow = function(parentDiv, keyName, addFunction, existingLocales) {
    var availableLocales = PWM_GLOBAL['localeInfo'];

    var tableRowElement = document.createElement('tr');
    tableRowElement.setAttribute("style","border-width: 0");

    var bodyHtml = '';
    bodyHtml += '<td style="border-width: 0" colspan="5">';
    bodyHtml += '<select id="' + keyName + '-addLocaleValue">';

    var localesAdded = 0;
    for (var localeIter in availableLocales) {
        if (localeIter != PWM_GLOBAL['defaultLocale']) {
            if (!existingLocales || (existingLocales.indexOf(localeIter) == -1)) {
                localesAdded++;
                var labelText = availableLocales[localeIter] + " (" + localeIter + ")";
                bodyHtml += '<option value="' + localeIter + '">' + labelText + '</option>';
            }
        }
    }
    bodyHtml += '</select>';

    bodyHtml += '<button type="button" class="btn" id="' + keyName + '-addLocaleButton"><span class="btn-icon fa fa-plus-square"></span>Add Locale</button>'

    bodyHtml += '</td>';
    if (localesAdded == 0) {
        bodyHtml = '<td style="border-width: 0" colspan="5"><span class="footnote">All locales present</span></td>';
    }
    tableRowElement.innerHTML = bodyHtml;
    PWM_MAIN.getObject(parentDiv).appendChild(tableRowElement);

    PWM_MAIN.addEventHandler(keyName + '-addLocaleButton','click',function(){
        var value = PWM_MAIN.getObject(keyName + "-addLocaleValue").value;
        addFunction(value);
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
        if (value.match('^[0-9]*$') == null) {
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

        if (!PWM_MAIN.isEmpty(data['data']['profileList'])) {
            body += '<div style="text-align: center">LDAP Profile <select id="select-profileList"></select></div><br/>';
        }
        body += '<div style="text-align: center">';
        if (currentDN && currentDN.length > 0 ) {
            body += currentDN;
        } else {
            body += '[root]';
        }
        body += '</div><br/>';

        body += '<div style="min-width:500px; max-height: 400px; overflow-y: auto; overflow-x:hidden; white-space: nowrap">';
        body += '<table class="noborder">';
        if ('parentDN' in data['data']) {
            var parentDN = data['data']['parentDN'];
            body += '<tr><td style="width:10px" class="navigableDN" data-dn="' + parentDN + '"><span class="fa fa-level-up"></span></td>';
            body += '<td title="' + parentDN + '">[parent]</td>';
            body += '</td>';
            body += '</tr>';
        }

        var makeEntryHtml = function(dnInformation,navigable) {
            var loopDN = dnInformation['dn'];
            var entryName = dnInformation['entryName'];
            var out = '';
            if (navigable) {
                out += '<tr><td style="width:10px" class="navigableDN" data-dn="' + loopDN + '"><span class="fa fa-level-down"></span></td>';
            } else {
                out += '<tr><td style="width:10px"></td>';
            }
            out += '<td class="selectableDN" data-dn="' + loopDN + '" title="' + loopDN + '"><a><code>' + entryName + '</code></a></td>';
            out += '</tr>';
            return out;
        };

        if (data['data']['navigableDNlist'] && !PWM_MAIN.isEmpty(data['data']['navigableDNlist'])) {
            var navigableDNlist = data['data']['navigableDNlist'];
            for (var i in navigableDNlist) {
                body += makeEntryHtml(navigableDNlist[i],true);
            }
        }
        if (data['data']['selectableDNlist'] && !PWM_MAIN.isEmpty(data['data']['selectableDNlist'])) {
            var selectableDNlist = data['data']['selectableDNlist'];
            for (var i in selectableDNlist) {
                body += makeEntryHtml(selectableDNlist[i],false);
            }
        }
        body += '</table></div>';

        if (data['data']['maxResults']) {
            body += '<div class="footnote">' + PWM_MAIN.showString('Display_SearchResultsExceeded') + '</div>';
        }

        body += '<div class="buttonbar"><button class="btn" id="button-editDN"><span class="btn-icon fa fa-edit"></span>Edit Text</button>';
        body += '<button class="btn" id="button-clearDN"><span class="btn-icon fa fa-times"></span>Clear Value</button></div>';

        PWM_MAIN.showDialog({title:'LDAP Browser',dialogClass:'auto',showOk:false,showClose:true,text:body,loadFunction:function(){
            PWM_MAIN.addEventHandler('button-editDN','click',function(){
                UILibrary.stringEditorDialog({value:currentDN,completeFunction:nextFunction});
            });
            PWM_MAIN.addEventHandler('button-clearDN','click',function(){
                nextFunction('');
                PWM_MAIN.closeWaitDialog();
            });

            PWM_MAIN.doQuery(".selectableDN",function(element){
                var dnValue = element.getAttribute("data-dn");
                PWM_MAIN.addEventHandler(element,'click',function(){
                    nextFunction(dnValue);
                    PWM_MAIN.closeWaitDialog();
                });
            });

            PWM_MAIN.doQuery(".navigableDN",function(element){
                var dnValue = element.getAttribute("data-dn");
                PWM_MAIN.addEventHandler(element,'click',function(){
                    UILibrary.editLdapDN(nextFunction,{profile:profile,currentDN:dnValue});
                });
            });

            if (!PWM_MAIN.isEmpty(data['data']['profileList'])) {
                var profileList = data['data']['profileList'];
                var profileSelect = PWM_MAIN.getObject('select-profileList');
                for (var i in profileList) {
                    (function(loopProfile) {
                        var optionElement = document.createElement('option');
                        optionElement.innerHTML = loopProfile;
                        optionElement.value = loopProfile;
                        if (loopProfile == profile) {
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
