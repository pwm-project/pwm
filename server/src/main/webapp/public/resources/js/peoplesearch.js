/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
        if (PWM_PS.peopleSearchService) PWM_PS.peopleSearchService.updateData(data['data']['searchResults']);

//        var grid = PWM_VAR['peoplesearch_search_grid'];
        if (data['error']) {
//            grid.refresh();
            PWM_MAIN.showErrorDialog(data);
        } else {

            var gridData = data['data']['searchResults'];
            var sizeExceeded = data['data']['sizeExceeded'];
//            grid.refresh();
//            grid.renderArray(gridData);
//            var sortState = grid.get("sort");
//            grid.set("sort", sortState);


            if (sizeExceeded) {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'inherit';
                PWM_MAIN.showTooltip({
                    id: 'maxResultsIndicator',
                    position: 'below',
                    text: PWM_MAIN.showString('Display_SearchResultsExceeded')
                })
            } else if (PWM_MAIN.JSLibrary.isEmpty(data['data']['searchResults']) && validationProps['usernameField'].length > 0) {
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
    var blankSrc = PWM_MAIN.addPwmFormIDtoURL(PWM_GLOBAL['url-resources'] + '/UserPhoto.png');
    var htmlBody = '';

    htmlBody += '<div class="panel-peoplesearch-person" id="panel-orgChart-person-' + data['userKey'] + '">';

    if (PWM_VAR['enablePhoto']) {
        htmlBody += '<div class="panel-peoplesearch-userDetailPhoto" id="panel-userPhoto-' + data['userKey'] + '">';
        htmlBody += '<img class="img-peoplesearch-userDetailPhoto" src="' + blankSrc + '">';
        htmlBody += '</div>';
    }

    htmlBody += '<div class="panel-peoplesearch-displayNames">';
    var loopID = 1;
    for (var iter in data['displayNames']) {
        (function(displayName){
            htmlBody += '<div class="panel-orgChart-displayName-' + loopID + '">';
            htmlBody += data['displayNames'][displayName];
            htmlBody += '</div>'; //4
            loopID++;
        })(iter);
    }
    htmlBody += '</div>'; //3
    htmlBody += '</div>';

    htmlBody += '<div id="peopleSearch-userDetailWrapper">';

//    if (data['hasOrgChart']) {
    htmlBody += '<div style="text-align: center"><button class="btn" id="button-peoplesearch-orgChart">'
        + '<span class="btn-icon pwm-icon pwm-icon-sitemap"></span>'
        + PWM_MAIN.showString('Title_OrgChart')
        + '</div>';
//    }

    htmlBody += '<table class="peopleSearch-userDetails">';
    for (var iter in data['detail']) {
        (function(iterCount){
            var attributeData = data['detail'][iterCount];
            var label = attributeData['label'];
            var type = attributeData['type'];
            htmlBody += '<tr><td class="key">' + label + '</td><td><div style="width:100%">';

            if (type === 'userDN') {
                var userReferences = attributeData['userReferences'];
                htmlBody += '<div style="max-height: 100px; overflow-y: auto">';
                for (var refIter in userReferences) {
                    (function(refIterInner){
                        var reference = userReferences[refIterInner];
                        var userKey = reference['userKey'];
                        var displayValue = reference['displayName'];
                        htmlBody += '<a id="link-' + userKey + '-' + reference + '">';
                        htmlBody += displayValue;
                        htmlBody += "</a>";
                        htmlBody += "<br/>";
                    })(refIter);
                }
                htmlBody += '</div>';
            } else {
                var values = attributeData['values'];
                for (var i in values) {
                    if (i > 0) {
                        htmlBody += '</br>'
                    }
                    if (type === 'email') {
                        htmlBody += '<a href="mailto:' + values[i] + '">' + values[i] + '</a>';
                    } else {
                        htmlBody += values[i];
                    }
                    if (attributeData['searchable'] === true) {
                        var likeSearchID = 'link-' + attributeData['name'] + '-' + values[i] + '-likeUserSearch';
                        htmlBody += '<span id="' + likeSearchID + '" class="icon-likeUserSearch btn-icon pwm-icon pwm-icon-search" title="' + PWM_MAIN.showString('Button_Search') + ' &quot;' + values[i] + '&quot;"></span>';
                    }
                }
            }
            htmlBody += '</div></td>'
        })(iter);
    }
    htmlBody += '</table></div>';

    return htmlBody;
};

PWM_PS.applyEventHandlersToDetailView = function(data) {
    var photoURL = data['photoURL'];
    if (photoURL) {
        var photoDiv = PWM_MAIN.getObject('panel-userPhoto-' + data['userKey']);
        PWM_PS.loadPicture(photoDiv,photoURL,'img-peoplesearch-userDetailPhoto');
    }

//  TODO: need to get this setting from: /peoplesearch?processAction=clientData, see line 426
//    if (data['hasOrgChart']) {
    PWM_MAIN.addEventHandler('button-peoplesearch-orgChart', 'click', function () {

        // Access the angular StateService on this page, and use it to navigate to the orgchart using the provided person ID
        angular.element(document.body).injector().get('$state').go('orgchart', {personId: data['userKey']});

        PWM_MAIN.clearDijitWidget('dialogPopup');
//            PWM_PS.showOrgChartView(data['userKey']);
    });
//    }


    for (var iter in data['detail']) {
        (function(iterCount){
            var attributeData = data['detail'][iterCount];
            if (attributeData['searchable'] === true) {
                var values = attributeData['values'];
                for (var i in values) {
                    var likeSearchID = 'link-' + attributeData['name'] + '-' + values[i] + '-likeUserSearch';
                    PWM_MAIN.addEventHandler(likeSearchID,'click',function(){
                        PWM_PS.submitLikeUserSearch(values[i]);
                    });
                }
            }
            var type = attributeData['type'];
            if (type === 'userDN') {
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
    PWM_MAIN.showWaitDialog({
        loadFunction:function(){
            var url = "PeopleSearch?processAction=detail&userKey=" + userKey;
            var loadFunction = function(data) {
                if (data['error'] === true) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showErrorDialog(data);
                    return;
                }
                var htmlBody = PWM_PS.convertDetailResultToHtml(data['data']);
                PWM_MAIN.showDialog({
                    title:'Details',
                    allowMove:true,
                    showOk:false,
                    text:htmlBody,
                    showClose:true,
                    loadFunction:function(){
                        PWM_PS.applyEventHandlersToDetailView(data['data']);
                        setTimeout(function(){PWM_MAIN.clearFocus()},10);
                    }
                });
            };
            PWM_MAIN.ajaxRequest(url, loadFunction, {method:'GET'});
        }
    });
};



PWM_PS.convertOrgChartDataToOrgChartHtml = function(data) {
    var makePanel = function(userReference, type, direction) {
        var userKey = userReference['userKey'];

        var output = '';
        output += '<div class="panel-orgChart-' + type + '">';  //1
        output += '<div class="panel-orgChart-person" id="panel-orgChart-person-' + userKey + '">'; //2
        if (userReference['hasMoreNodes']) {
            output += '<a id="link-' + userKey + '"><span class="icon-orgChart-' + direction + ' pwm-icon pwm-icon-arrow-' + direction + '"/> </a>';
        }
        if (PWM_VAR['enablePhoto']) {
            var blankSrc = PWM_MAIN.addPwmFormIDtoURL(PWM_GLOBAL['url-resources'] + '/UserPhoto.png');
            output += '<div class="panel-orgChart-userPhoto" id="panel-userPhoto-' + userKey + '">';
            output += '<img class="img-orgChart-userPhoto" src="' + blankSrc + '">';
            output += '</div>';
        }
        output += '<div class="panel-orgChart-displayNames">'; //3
        var loopID = 1;
        for (var iter in userReference['displayNames']) {
            (function(displayName){
                output += '<div class="panel-orgChart-displayName-' + loopID + '">';  //4
                output += userReference['displayNames'][displayName];
                output += '</div>'; //4
                loopID++;
            })(iter);
        }
        output += '</div>'; //3
        output += '</div>'; //2
        output += '</div>'; //1
        return output;
    };

    var htmlOutput = '';
    htmlOutput += '<div class="panel-orgChart">'; //1
    if ('parent' in data) {
        htmlOutput += makePanel(data['parent'], 'parent', 'up');
    }
    if ('siblings' in data) {
        htmlOutput += '<div class="panel-orgChart-siblings">'; //2
        for (var iter in data['siblings']) {
            (function(iterCount){
                var siblingReference = data['siblings'][iterCount];
                htmlOutput += makePanel(siblingReference, 'sibling', 'down');
            })(iter);
        }
        htmlOutput += '</div>'; //2
    }
    htmlOutput += '<div class="panel-orgChart-footer"></div>';
    htmlOutput += '</div>'; //1
    return htmlOutput;
};

PWM_PS.applyOrgChartDataToOrgChartEvents = function(data) {
    var applyEventsToReference = function(reference,asParent) {
        PWM_MAIN.addEventHandler('panel-orgChart-person-' + reference['userKey'],'click',function(){
            PWM_PS.showUserDetail(reference['userKey']);
        });
        if (reference['hasMoreNodes']) {
            PWM_MAIN.addEventHandler('link-' + reference['userKey'], 'click', function (e) {
                PWM_MAIN.stopEvent(e);
                PWM_PS.showOrgChartView(reference['userKey'],asParent)
            });
        }
        if ('photoURL' in reference) {
            var photoURL = reference['photoURL'];
            var parentDiv = PWM_MAIN.getObject('panel-userPhoto-' + reference['userKey']);
            PWM_PS.loadPicture(parentDiv, photoURL,'img-orgChart-userPhoto');
        }

    };
    if ('parent' in data) {
        applyEventsToReference(data['parent'],false);
    }
    if ('siblings' in data) {
        for (var iter in data['siblings']) {
            (function(iterCount){
                var siblingReference = data['siblings'][iterCount];
                applyEventsToReference(siblingReference,true);
            })(iter);
        }
    }
};


PWM_PS.showOrgChartView = function(userKey, asParent) {
    console.log('beginning showOrgChartView, userKey=' + userKey);
    var sendData = {
        userKey:userKey,
        asParent:asParent
    };
    PWM_MAIN.showWaitDialog({
        loadFunction:function(){
            var url = "PeopleSearch?processAction=orgChartData";
            var loadFunction = function(data) {
                if (data['error'] === true) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showErrorDialog(data);
                    return;
                }
                var htmlBody = PWM_PS.convertOrgChartDataToOrgChartHtml(data['data']);
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showDialog({
                    title:PWM_MAIN.showString('Title_OrgChart'),
                    allowMove:true,
                    showOk:false,
                    text:htmlBody,
                    dialogClass:'orgChart',
                    showClose:true,
                    loadFunction:function(){
                        PWM_PS.applyOrgChartDataToOrgChartEvents(data['data']);
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
//            PWM_MAIN.getObject('peoplesearch-searchResultsGrid').innerHTML = '';

            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

//            PWM_VAR['peoplesearch_search_grid'] = new CustomGrid({
//                columns: PWM_VAR['peoplesearch_search_columns'],
//                queryOptions: {
//                    sort: [{ attribute: "sn" }]
//                }
//            }, "peoplesearch-searchResultsGrid");

            if (nextFunction) {
                nextFunction();
            }

//            PWM_VAR['peoplesearch_search_grid'].on(".dgrid-row:click", function(evt){
//                PWM_MAIN.stopEvent(evt);
//                evt.preventDefault();
//                var row = PWM_VAR['peoplesearch_search_grid'].row(evt);
//                var userKey = row.data['userKey'];
//                PWM_PS.showUserDetail(userKey);
//            });

//            PWM_VAR['peoplesearch_search_grid'].set("sort", { attribute : 'sn', descending: true});

        }
    );
};

PWM_PS.loadPicture = function(parentDiv,url,cssClass) {
    var image = new Image();
    image.setAttribute('class',cssClass);
    PWM_MAIN.addEventHandler(image,"load",function(){
        if (parentDiv) {
            while (parentDiv.firstChild) {
                parentDiv.removeChild(parentDiv.firstChild);
            }
            parentDiv.appendChild(image);
        }
    });
    image.src = url;
};

PWM_PS.initPeopleSearchPage = function() {
    var applicationData = PWM_MAIN.getObject("application-info");
    var jspName = applicationData ? applicationData.getAttribute("data-jsp-name") : "";
    if ("peoplesearch.jsp" === jspName) {
        var url = PWM_MAIN.addParamToUrl(window.location.href.replace(window.location.hash, ''),'processAction','clientData');
        PWM_MAIN.ajaxRequest(url,function(data){
            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
                return;
            }
            for (var keyName in data['data']) {
                PWM_VAR[keyName] = data['data'][keyName];
            }
            console.log('loaded peoplesearch jsClientData');
            PWM_PS.makeSearchGrid(function () {

                try {
                    var fieldUsername = PWM_MAIN.Preferences.readSessionStorage("peoplesearch_field_username","");
                    PWM_MAIN.getObject('username').value = fieldUsername;
                } catch (e) {
                    console.log('error reading username field from sessionStorage: ' + e);
                }

                PWM_MAIN.addEventHandler('username', "keyup, input", function () {
                    try {
                        var fieldUsername = PWM_MAIN.getObject('username').value;
                        PWM_MAIN.Preferences.writeSessionStorage("peoplesearch_field_username", fieldUsername);
                    } catch (e) {
                        console.log('error writing username field from sessionStorage: ' + e);
                    }

                    PWM_PS.processPeopleSearch();
                });
                if (PWM_MAIN.getObject('username')) {
                    if (PWM_MAIN.getObject('username').value && PWM_MAIN.getObject('username').value.length > 0) {
                        PWM_PS.processPeopleSearch();
                    }
                }
            });
        },{method:"GET"});
    }
};

//PWM_PS.initPeopleSearchPage();
