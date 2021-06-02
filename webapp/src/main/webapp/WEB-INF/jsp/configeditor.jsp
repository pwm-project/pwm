<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.tag.url.PwmThemeURL" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_MOBILE_CSS); %>
<link href="<pwm:url url='/public/resources/webjars/dijit/themes/nihilo/nihilo.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<style nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" type="text/css">
    html { overflow-y: scroll; } <%-- always add verticle scrollbar to page --%>
</style>
<div id="wrapper">
    <div class="configeditor-header" id="header" >
        <div id="header-center-wide" style="min-width: 850px">
            <div id="header-title">
                <span id="currentPageDisplay"></span>
                <span style="visibility: hidden" id="working_icon" class="headerIcon pwm-icon pwm-icon-cog pwm-icon-spin"></span>

                <div class="headerIcon" id="referenceDoc_icon" title="<pwm:display key="Tooltip_OpenReferenceDocButton" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-book"></span>
                </div>

                <span id="idle_status" class="editorIdleStatus">
                    <%--idle timeout text --%>
                </span>
            </div>
        </div>
    </div>
    <div id="centerbody-config" class="centerbody-config" ng-app="configeditor.module" ng-controller="ConfigEditorController as $ctrl">

        <div id="settingSearchPanel">
            <div style="float:left; width: 49%">
                <table class="noborder settingSearchPanelTable">
                    <colgroup>
                        <col class="settingSearchPanelTable_Col1">
                        <col class="settingSearchPanelTable_Col2">
                        <col class="settingSearchPanelTable_Col3">
                    </colgroup>
                    <tr>
                        <td>
                            <span id="settingSearchIcon" class="pwm-icon pwm-icon-search" title="<pwm:display key="Tooltip_IconSettingsSearch" bundle="Config"/>"></span>
                        </td>
                        <td>
                            <input placeholder="<pwm:display key="Placeholder_Search"/>" type="search" id="homeSettingSearch" name="homeSettingSearch" class="inputfield" placeholder="Search Configuration" autocomplete="off" <pwm:autofocus/>/>
                        </td>
                        <td>
                            <div style="margin-top:5px; width:20px; max-width: 20px;">
                                <div id="indicator-searching" class="hidden">
                                    <span style="" class="pwm-icon pwm-icon-lg pwm-icon-spin pwm-icon-spinner"></span>
                                </div>
                                <div id="indicator-noResults" class="hidden" title="<pwm:display key="Tooltip_IconSearchNoResults" bundle="Config"/>">
                                    <span style="color: #ffcd59;" class="pwm-icon pwm-icon-lg pwm-icon-ban"></span>
                                </div>
                            </div>
                        </td>
                    </tr>
                </table>
            </div>
            <div style="float: right;">
                <table class="noborder nopadding nomargin" width="5%">
                    <tr>
                        <td>
                            <a id="macroDoc_icon" class="configLightText">Macro Help</a><br/>
                            <a id="setPassword_icon" class="configLightText">Config Password</a>
                        </td>
                        <td>
                            <label title="<pwm:display key="Tooltip_SaveEditorButton" bundle="Config"/>">
                                <button id="saveButton_icon">Save</button>
                            </label>
                        </td>
                        <td>
                            <label title="<pwm:display key="Tooltip_CancelEditorButton" bundle="Config"/>">
                                <button  id="cancelButton_icon">Cancel</button>
                            </label>
                        </td>

                    </tr>
                </table>
            </div>
            <div id="searchResults" class="hidden">
                <%-- search results inserted here --%>
            </div>
        </div>
        <div id="navigationTreeWrapper">
            <div id="navigationTree">
                <%-- navtree goes here --%>
            </div>


            <div id="navTreeExpanderButtons">
                <div>
                    <span id="button-navigationExpandAll" class="pwm-icon pwm-icon-plus-square" title="<pwm:display key="Tooltip_IconExpandAll" bundle="Config"/>"></span>
                    &nbsp;&nbsp;
                    <span id="button-navigationCollapseAll" class="pwm-icon pwm-icon-minus-square" title="<pwm:display key="Tooltip_IconCollapseAll" bundle="Config"/>"></span>
                </div>
                <div>
                    <div class="toggleWrapper">
                        <div class="toggle" id="radio-setting-level">
                            <input type="radio" name="radio-setting-level" value="2" id="radio-setting-level-2" />
                            <label for="radio-setting-level-2"><pwm:display key="Display_SettingFilter_Level_2" bundle="Config"/></label>
                            <input type="radio" name="radio-setting-level" value="1" id="radio-setting-level-1" />
                            <label for="radio-setting-level-1"><pwm:display key="Display_SettingFilter_Level_1" bundle="Config"/></label>
                            <input type="radio" name="radio-setting-level" value="0" id="radio-setting-level-0" />
                            <label for="radio-setting-level-0"><pwm:display key="Display_SettingFilter_Level_0" bundle="Config"/></label>
                        </div>
                    </div>
                    <div class="toggleWrapper">
                        <div class="toggle" id="radio-modified-only" >
                            <input type="radio" name="radio-modified-only" value="all" id="radio-modified-only-all" />
                            <label for="radio-modified-only-all">All Settings</label>
                            <input type="radio" name="radio-modified-only" value="modified" id="radio-modified-only-modified" />
                            <label for="radio-modified-only-modified">Modified Only</label>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div id="settingsPanel">
            <%-- settings content goes here --%>
        </div>
        <div id="infoPanel">
            <%-- info panel goes here --%>
        </div>
        <div id="config-infoPanel"></div>
    </div>
    <br/><br/>
    <div class="push"></div>
</div>

<%--
<div id="header-warning" style="display: none">
    <div class="header-warning-row header-warning-version"><%=PwmConstants.PWM_APP_NAME_VERSION%></div>
    <div id="header-warning-message" class="header-warning-row header-warning-message">
        configeditor
    </div>
    <div class="header-warning-row header-warning-buttons">
        <a class="header-warning-button" id="header_configManagerButton">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-gears"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigManager" bundle="Admin"/>
        </a>
        <a class="header-warning-button" id="header_configEditorButton">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-edit"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
        </a>
    </div>
    <div id="button-closeMenu" title="<pwm:display key="Button_Hide"/>">
        <span class="pwm-icon pwm-icon-chevron-circle-up"></span>
    </div>
</div>
<div id="button-openMenu" title="<pwm:display key="Button_Show"/>">
    <span class="pwm-icon pwm-icon-chevron-circle-down"></span>
</div>
--%>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_CFGEDIT.initConfigEditor();
            PWM_CONFIG.initConfigHeader();
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-action.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-email.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-form.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-challenges.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-customlink.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-remotewebservices.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-permissions.js"/>
<pwm:script-ref url="/public/resources/js/configeditor.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>

<%--Provide the angular code we made specifically for this page:--%>
<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/webjars/pwm-client/vendor/textangular/textAngular.css' addContext="true"/>"/>
<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/html-editor.css' addContext="true"/>"/>
<pwm:script-ref url="/public/resources/webjars/pwm-client/vendor.js" />
<pwm:script-ref url="/public/resources/webjars/pwm-client/configeditor.ng.js" />
<%--/ Provide the angular code we made specifically for this page:--%>

<%@ include file="fragment/footer.jsp" %>
</body>
</html>
