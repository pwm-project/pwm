<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<% final String configNotes = configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES);%>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper" style="border:1px; height: 100%" >
    <div id="header" style="height: 25px; position: fixed;">
        <div id="header-center">
            <div id="header-title">
                <%=PwmConstants.PWM_APP_NAME%> Configuration Editor <span id="currentPageDisplay"></span>
                <span style="visibility: hidden; color: orange" id="working_icon" class="headerIcon fa fa-cog fa-spin"></span>
                <div class="headerIcon" style="float: right" id="cancelButton_icon">
                    <span class="fa fa-times"></span>
                </div>
                <div class="headerIcon" style="float: right" id="saveButton_icon">
                    <span class="fa fa-save"></span>
                </div>
                <div class="headerIcon" style="float: right" id="setPassword_icon">
                    <span class="fa fa-key"></span>
                </div>
                <div class="headerIcon" style="float: right" id="referenceDoc_icon">
                    <span class="fa fa-book"></span>
                </div>
                <div class="headerIcon" style="float: right" id="macroDoc_icon">
                    <span class="fa fa-gears"></span>
                </div>
            </div>
        </div>
    </div>
    <div id="centerbody-config" class="centerbody-config">
        <div id="settingSearchPanel">
            <table style="width:600px; margin-left: auto; margin-right: auto" class="noborder">
                <tr>
                    <td style="width:10px">
                        <span id="settingSearchIcon" class="fa fa-search"></span>
                    </td>
                    <td style="width:580px">
                        <input type="search" id="homeSettingSearch" name="homeSettingSearch" class="inputfield" style="width: 580px" autofocus/>
                    </td>
                    <td style="width:10px">
                        <div id="searchIndicator" style="display: none">
                            <span style="" class="fa fa-lg fa-spin fa-spinner"></span>
                        </div>
                        <div id="noSearchResultsIndicator" style="display: none;">
                            <span style="color: #ffcd59;" class="fa fa-lg fa-ban"></span>
                        </div>
                    </td>
                </tr>
            </table>
            <div id="searchResults" style="visibility: hidden">
            </div>
        </div>
        <div id="navigationTreeWrapper" style="margin-top: 57px">
            <div id="navigationTreeTopMenu">
                <span id="button-navigationExpandAll" class="fa fa-plus-square"></span>
                <span id="button-navigationCollapseAll" class="fa fa-minus-square"></span>
            </div>
            <div id="navigationTree" style="">
                <!-- navtree goes here -->
            </div>
        </div>
        <div id="settingsPanel" style="width:600px;float:right; border:0;margin-top: 57px">
        </div>
    </div>
    <br/><br/>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['setting_alwaysFloatMessages'] = true;
        var PWM_VAR = PWM_VAR || {};
        PWM_VAR['currentTemplate'] = '<%=configManagerBean.getConfiguration().getTemplate()%>';
        PWM_VAR['configurationNotes'] = '<%=configNotes==null?"":StringUtil.escapeJS(configNotes)%>';
        PWM_VAR['ldapProfileIds'] = [];
        <% for (final String id : (List<String>)configManagerBean.getConfiguration().readSetting(PwmSetting.LDAP_PROFILE_LIST).toNativeObject()) { %>
        PWM_VAR['ldapProfileIds'].push('<%=StringUtil.escapeJS(id)%>');
        <% } %>

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_CFGEDIT.initConfigEditor();
        });

    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configeditor-settings.js"/>"></script>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/admin.js"/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
