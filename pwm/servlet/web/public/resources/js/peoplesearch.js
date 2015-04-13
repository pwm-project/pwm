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


"use strict";

var PWM_PS = PWM_PS || {};
var PWM_VAR = PWM_VAR || {};


PWM_PS.processPeopleSearch = function() {
    var validationProps = {};
    validationProps['serviceURL'] = "PeopleSearch?processAction=search";
    validationProps['showMessage'] = false;
    validationProps['ajaxTimeout'] = 120 * 1000;
    validationProps['usernameField'] = PWM_MAIN.getObject('username').value;
    validationProps['readDataFunction'] = function(){
        PWM_MAIN.getObject('searchIndicator').style.display = 'inherit';
        return { username:PWM_MAIN.getObject('username').value }
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.getObject('searchIndicator').style.display = 'none';
    };
    validationProps['processResultsFunction'] = function(data) {
        var grid = PWM_VAR['peoplesearch_search_grid'];
        if (data['error']) {
            grid.refresh();
            PWM_MAIN.showErrorDialog(data);
        } else {

            var gridData = data['data']['searchResults'];
            var sizeExceeded = data['data']['sizeExceeded'];
            grid.refresh();
            grid.renderArray(gridData);
            grid.on(".dgrid-row:click", function(evt){
                if (PWM_VAR['detailInProgress'] != true) {
                    PWM_VAR['detailInProgress'] = true;
                    evt.preventDefault();
                    var row = grid.row(evt);
                    var userKey = row.data['userKey'];
                    PWM_PS.showUserDetail(userKey);
                } else {
                    console.log('ignoring dupe detail request event');
                }
            });

            if (sizeExceeded) {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'inherit';
                PWM_MAIN.showTooltip({
                    id: 'maxResultsIndicator',
                    position: 'below',
                    text: PWM_MAIN.showString('Display_SearchResultsExceeded')
                })
            } else if (PWM_MAIN.isEmpty(data['data']['searchResults']) && validationProps['usernameField'].length > 0) {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'inherit';
                PWM_MAIN.showTooltip({
                    id: 'maxResultsIndicator',
                    position: 'below',
                    text: PWM_MAIN.showString('Display_SearchResultsNone')
                })
            } else {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'none';
            }
        }
    };
    PWM_MAIN.pwmFormValidator(validationProps);
    PWM_MAIN.getObject('maxResultsIndicator').style.display = 'none';
};

PWM_PS.convertDetailResultToHtml = function(data) {
    var htmlBody = '';
    if (data['photoURL']) {
        var blankSrc = PWM_MAIN.addPwmFormIDtoURL(PWM_GLOBAL['url-resources'] + '/dojo/dojo/resources/blank.gif');
        var styleAttr = PWM_VAR['photo_style_attribute'];
        htmlBody += '<div id="userPhotoParentDiv"><img style="' + styleAttr + '" src="' + blankSrc + '"></div>';
    }
    htmlBody += '<div id="peopleSearch-userDetailWrapper">';
    htmlBody += '<table class="peopleSearch-userDetails">';
    for (var iter in data['detail']) {
        (function(iterCount){
            var attributeData = data['detail'][iterCount];
            var label = attributeData['label'];
            var type = attributeData['type'];
            htmlBody += '<tr><td class="key">' + label + '</td><td><div style="width:100%">';

            if (type == 'userDN') {
                var userReferences = attributeData['userReferences'];
                htmlBody += '<div style="max-height: 100px; overflow-y: auto">';
                for (var refIter in userReferences) {
                    (function(refIterInner){
                        var reference = userReferences[refIterInner];
                        var userKey = reference['userKey'];
                        var displayValue = reference['display'];
                        htmlBody += '<a id="link-' + userKey + '-' + reference + '">';
                        htmlBody += displayValue;
                        htmlBody += "</a><br/>";
                    })(refIter);
                }
                htmlBody += '</div>';
            } else if (type == 'email') {
                var value = attributeData['value'];
                htmlBody += '<a href="mailto:' + value + '">' + value + '</a>';
            } else {
                htmlBody += attributeData['value'];
                if (attributeData['searchable'] == true) {
                    var likeSearchID = 'link-' + attributeData['name'] + '-' + '-likeUserSearch';
                    htmlBody += '<span id="' + likeSearchID + '" class="icon-likeUserSearch btn-icon fa fa-search"></span>';
                }
            }
            htmlBody += '</div></td>'
        })(iter);
    }
    htmlBody += '</table></div>';
    return htmlBody;
};

PWM_PS.applyEventHandlersToDetailView = function(data) {
    for (var iter in data['detail']) {
        (function(iterCount){
            var attributeData = data['detail'][iterCount];
            if (attributeData['searchable'] == true) {
                var likeSearchID = 'link-' + attributeData['name'] + '-' + '-likeUserSearch';
                PWM_MAIN.addEventHandler(likeSearchID,'click',function(){
                    PWM_PS.submitLikeUserSearch(attributeData['value']);
                });
            }
            var type = attributeData['type'];
            if (type == 'userDN') {
                var userReferences = attributeData['userReferences'];
                for (var refIter in userReferences) {
                    (function(refIterInner){
                        var reference = userReferences[refIterInner];
                        var userKey = reference['userKey'];
                        PWM_MAIN.addEventHandler('link-' + userKey + '-' + reference,'click',function(){
                            PWM_PS.showUserDetail(userKey);
                        });
                    })(refIter);
                }
            }
        })(iter);
    }
};

PWM_PS.submitLikeUserSearch = function(value) {
    console.log('starting like search for value: ' + value);
    PWM_MAIN.closeWaitDialog();
    PWM_MAIN.getObject('username').value = value;
    PWM_PS.processPeopleSearch();
};

PWM_PS.showUserDetail = function(userKey) {
    console.log('beginning showUserDetail, userKey=' + userKey);
    var sendData = {
        userKey:userKey
    };
    PWM_MAIN.showWaitDialog({
        loadFunction:function(){
            PWM_VAR['detailInProgress'] = false;
            var url = "PeopleSearch?processAction=detail";
            var loadFunction = function(data) {
                if (data['error'] == true) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showErrorDialog(data);
                    return;
                }
                var htmlBody = PWM_PS.convertDetailResultToHtml(data['data']);
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showDialog({
                    title:data['data']['displayName'],
                    allowMove:true,
                    text:htmlBody,
                    showClose:true,
                    loadFunction:function(){
                        var photoURL = data['data']['photoURL'];
                        if (photoURL) {
                            PWM_PS.loadPicture(PWM_MAIN.getObject("userPhotoParentDiv"),photoURL);
                        }
                        PWM_PS.applyEventHandlersToDetailView(data['data']);
                        setTimeout(function() {
                            try {PWM_MAIN.getObject('dialog_ok_button').focus(); } catch (e) { /*noop */}
                        },1000);
                    }
                });
            };
            PWM_MAIN.ajaxRequest(url, loadFunction, {content:sendData});
        }
    });
};

PWM_PS.makeSearchGrid = function(nextFunction) {
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
            function(dojo,declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                PWM_MAIN.getObject('peoplesearch-searchResultsGrid').innerHTML = '';

                var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                PWM_VAR['peoplesearch_search_grid'] = new CustomGrid({
                    columns: PWM_VAR['peoplesearch_search_columns']
                }, "peoplesearch-searchResultsGrid");

                if (nextFunction) {
                    nextFunction();
                }
            });
};

PWM_PS.loadPicture = function(parentDiv,url) {
    if (url.lastIndexOf('http', 0) !== 0) {
        url = PWM_MAIN.addPwmFormIDtoURL(url);
    }
    require(["dojo/on"], function(on){
        var image = new Image();
        image.setAttribute('id',"userPhotoImage");
        image.setAttribute('style',PWM_VAR['photo_style_attribute']);
        on(image,"load",function(){
            while (parentDiv.firstChild) {
                parentDiv.removeChild(parentDiv.firstChild);
            }
            parentDiv.appendChild(image);
        });
        image.src = url;
    });
};

PWM_PS.initPeopleSearchPage = function() {
    var applicationData = PWM_MAIN.getObject("application-info");
    var jspName = applicationData ? applicationData.getAttribute("data-jsp-name") : "";
    if ("peoplesearch.jsp" == jspName) {
        PWM_MAIN.ajaxRequest("PeopleSearch?processAction=clientData",function(data){
            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
                return;
            }
            for (var keyName in data['data']) {
                PWM_VAR[keyName] = data['data'][keyName];
            }
            console.log('loaded peoplesearch jsClientData');
            PWM_PS.makeSearchGrid(function () {
                PWM_MAIN.addEventHandler('username', "keyup, input", function () {
                    PWM_PS.processPeopleSearch();
                });
                if (PWM_MAIN.getObject('username').value && PWM_MAIN.getObject('username').value.length > 0) {
                    PWM_PS.processPeopleSearch();
                }
            });
        },{method:"GET"});
    }
};

PWM_PS.initPeopleSearchPage();