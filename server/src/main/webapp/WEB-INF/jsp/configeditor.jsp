
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2017 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
                <div class="headerIcon" id="cancelButton_icon" title="<pwm:display key="Tooltip_CancelEditorButton" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-times"></span>
                </div>
                <div class="headerIcon" id="saveButton_icon" title="<pwm:display key="Tooltip_SaveEditorButton" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-save"></span>
                </div>
                <div class="headerIcon" id="setPassword_icon" title="<pwm:display key="Tooltip_SetConfigPasswordButton" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-key"></span>
                </div>
                <div class="headerIcon" id="referenceDoc_icon" title="<pwm:display key="Tooltip_OpenReferenceDocButton" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-book"></span>
                </div>
                <div class="headerIcon" id="macroDoc_icon" title="<pwm:display key="Tooltip_OpenMacroHelpButton" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-magic"></span>
                </div>
                <span id="idle_status" class="editorIdleStatus">
                    <%--idle timeout text --%>
                </span>
            </div>
        </div>
    </div>
    <div id="centerbody-config" class="centerbody-config" ng-app="configeditor.module" ng-controller="ConfigEditorController as $ctrl">
        <div id="settingSearchPanel">
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
                        <input placeholder="<pwm:display key="Placeholder_Search"/>" type="search" id="homeSettingSearch" name="homeSettingSearch" class="inputfield" placeholder="Search Configuration" <pwm:autofocus/>/>
                    </td>
                    <td>
                        <div style="margin-top:5px; width:20px; max-width: 20px;">
                            <div id="indicator-searching" style="display: none">
                                <span style="" class="pwm-icon pwm-icon-lg pwm-icon-spin pwm-icon-spinner"></span>
                            </div>
                            <div id="indicator-noResults" style="display: none;" title="<pwm:display key="Tooltip_IconSearchNoResults" bundle="Config"/>">
                                <span style="color: #ffcd59;" class="pwm-icon pwm-icon-lg pwm-icon-ban"></span>
                            </div>
                        </div>
                    </td>
                </tr>
            </table>
            <div id="searchResults" style="visibility: hidden">
                <%-- search results inserted here --%>
            </div>
        </div>
        <div id="navigationTreeWrapper" style="display:none">
            <div id="navigationTree">
                <%-- navtree goes here --%>
            </div>
            <div id="navTreeExpanderButtons">
                <span id="button-navigationExpandAll" class="pwm-icon pwm-icon-plus-square" title="<pwm:display key="Tooltip_IconExpandAll" bundle="Config"/>"></span>
                &nbsp;&nbsp;
                <span id="button-navigationCollapseAll" class="pwm-icon pwm-icon-minus-square" title="<pwm:display key="Tooltip_IconCollapseAll" bundle="Config"/>"></span>
                &nbsp;&nbsp;
                <div class="headerIcon" id="settingFilter_icon" title="<pwm:display key="Tooltip_IconFilterSettings" bundle="Config"/>">
                    <span class="pwm-icon pwm-icon-filter"></span>
                </div>
            </div>
        </div>
        <div id="settingsPanel">
            <%-- settings content goes here --%>
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
<pwm:script-ref url="/public/resources/js/configeditor.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-challenges.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-customlink.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-remotewebservices.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>

<%--Add support for angular--%>
<pwm:script-ref url="/public/resources/webjars/angular/angular.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-ui-router/release/angular-ui-router.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-translate/dist/angular-translate.min.js" />
<%--/ Add support for angular--%>

<%--Add support for the "textAngular" library (a control for editing HTML)--%>
<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/webjars/textAngular/dist/textAngular.css' addContext="true"/>"/>
<pwm:script-ref url="/public/resources/webjars/textAngular/dist/textAngular-rangy.min.js" />
<pwm:script-ref url="/public/resources/webjars/textAngular/dist/textAngular-sanitize.min.js" />
<pwm:script-ref url="/public/resources/webjars/textAngular/dist/textAngular.min.js" />
<%--/ Add support for the "textAngular" library (a control for editing HTML)--%>

<%--Provide the angular code we made specifically for this page:--%>
<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/html-editor.css' addContext="true"/>"/>
<pwm:script-ref url="/public/resources/webjars/pwm-client/configeditor.ng.js" />
<%--/ Provide the angular code we made specifically for this page:--%>

<%@ include file="fragment/footer.jsp" %>
</body>
</html>
