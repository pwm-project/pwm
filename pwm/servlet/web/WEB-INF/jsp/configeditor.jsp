<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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

<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<%@ include file="fragment/header.jsp" %>
<% final PwmRequest configeditor_pwmRequest = PwmRequest.forRequest(request, response); %>
<% final ConfigManagerBean configManagerBean = configeditor_pwmRequest.getPwmSession().getConfigManagerBean(); %>
<% final boolean configUnlocked = configeditor_pwmRequest.getPwmApplication().getApplicationMode() == PwmApplication.MODE.CONFIGURATION && !PwmConstants.TRIAL_MODE; %>
<% final String configNotes = configManagerBean.getStoredConfiguration().readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES);%>
<body class="nihilo">
<style nonce="<pwm:value name="cspNonce"/>" type="text/css">
    html { overflow-y: scroll; } <%-- always add verticle scrollbar to page --%>
</style>
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <div class="configeditor-header" id="header" >
        <div id="header-center-wide" style="min-width: 850px">
            <div id="header-title">
                <%=PwmConstants.PWM_APP_NAME%> Configuration Editor <span id="currentPageDisplay"></span>
                <span style="visibility: hidden" id="working_icon" class="headerIcon fa fa-cog fa-spin"></span>
                <div class="headerIcon" id="cancelButton_icon" title="<pwm:display key="Tooltip_CancelEditorButton" bundle="Config"/>">
                    <span class="fa fa-times"></span>
                </div>
                <div class="headerIcon" id="saveButton_icon" title="<pwm:display key="Tooltip_SaveEditorButton" bundle="Config"/>">
                    <span class="fa fa-save"></span>
                </div>
                <div class="headerIcon" id="setPassword_icon" title="<pwm:display key="Tooltip_SetConfigPasswordButton" bundle="Config"/>">
                    <span class="fa fa-key"></span>
                </div>
                <div class="headerIcon" id="referenceDoc_icon" title="<pwm:display key="Tooltip_OpenReferenceDocButton" bundle="Config"/>">
                    <span class="fa fa-book"></span>
                </div>
                <div class="headerIcon" id="macroDoc_icon" title="<pwm:display key="Tooltip_OpenMacroHelpButton" bundle="Config"/>">
                    <span class="fa fa-magic"></span>
                </div>
                <span id="idle_status" class="editorIdleStatus">
                    <%--idle timeout text --%>
                </span>
            </div>
        </div>
    </div>
    <div id="centerbody-config" class="centerbody-config">
        <div id="settingSearchPanel">
            <table class="noborder settingSearchPanelTable">
                <colgroup>
                    <col class="settingSearchPanelTable_Col1">
                    <col class="settingSearchPanelTable_Col2">
                    <col class="settingSearchPanelTable_Col3">
                </colgroup>
                <tr>
                    <td>
                        <span id="settingSearchIcon" class="fa fa-search" title="<pwm:display key="Tooltip_IconSettingsSearch" bundle="Config"/>"></span>
                    </td>
                    <td>
                        <input type="search" id="homeSettingSearch" name="homeSettingSearch" class="inputfield" <pwm:autofocus/>/>
                    </td>
                    <td>
                        <div style="margin-top:5px; width:20px; max-width: 20px;">
                            <div id="indicator-searching" style="display: none">
                                <span style="" class="fa fa-lg fa-spin fa-spinner"></span>
                            </div>
                            <div id="indicator-noResults" style="display: none;" title="<pwm:display key="Tooltip_IconSearchNoResults" bundle="Config"/>">
                                <span style="color: #ffcd59;" class="fa fa-lg fa-ban"></span>
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
                <span id="button-navigationExpandAll" class="fa fa-plus-square" title="<pwm:display key="Tooltip_IconExpandAll" bundle="Config"/>"></span>
                &nbsp;&nbsp;
                <span id="button-navigationCollapseAll" class="fa fa-minus-square" title="<pwm:display key="Tooltip_IconCollapseAll" bundle="Config"/>"></span>
                &nbsp;&nbsp;
                <div class="headerIcon" id="settingFilter_icon" title="<pwm:display key="Tooltip_IconFilterSettings" bundle="Config"/>">
                    <span class="fa fa-filter"></span>
                </div>
            </div>
        </div>
        <div id="settingsPanel">
            <%-- settings content goes here --%>
        </div>
    </div>
    <br/><br/>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        var PWM_VAR = PWM_VAR || {};
        PWM_GLOBAL['setting_alwaysFloatMessages'] = true;
        PWM_VAR['currentTemplate'] = '<%=configManagerBean.getStoredConfiguration().getTemplate()%>';
        PWM_VAR['configurationNotes'] = '<%=configNotes==null?"":StringUtil.escapeJS(configNotes)%>';
        PWM_VAR['ldapProfileIds'] = [];
        <% for (final String id : (List<String>)configManagerBean.getStoredConfiguration().readSetting(PwmSetting.LDAP_PROFILE_LIST).toNativeObject()) { %>
        PWM_VAR['ldapProfileIds'].push('<%=StringUtil.escapeJS(id)%>');
        <% } %>

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_CFGEDIT.initConfigEditor();

            <% if (configUnlocked) { %>
            PWM_MAIN.showDialog({
                title:'Notice - Configuration Status: Open',
                text:PWM_CONFIG.showString('Display_ConfigOpenInfo'),
                loadFunction:function(){
                    PWM_MAIN.addEventHandler('link-configManager','click',function(){
                        PWM_MAIN.goto('/private/config/ConfigManager');
                    });
                }
            });

            <% } %>
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/configeditor.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
