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

<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.http.servlet.ConfigEditorServlet" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.TreeSet" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(PwmRequest.forRequest(request, response)); %>
<% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = cookie.getLocaleBundle(); %>
<% final ResourceBundle bundle = ResourceBundle.getBundle(bundleName.getTheClass().getName()); %>
<pwm:script>
<script type="text/javascript">
    var LOAD_TRACKER = new Array();
</script>
</pwm:script>
<div id="loadingDiv">
    <div class="WaitDialogBlank">
    </div>
    <noscript>
        <br/>
        Javasciript is required to view this page.
    </noscript>
</div>
<div id="localeEditor_body" style="display:none">
<div>
    <pwm:display key="Display_ConfigEditorLocales" bundle="Config" value1="<%=PwmConstants.PWM_URL_HOME%>"/>
</div>
<% for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) { %>
<% final boolean isDefault = PwmSession.getPwmSession(session).getConfigManagerBean().getConfiguration().readLocaleBundleMap(bundleName.getTheClass().getName(),key).isEmpty();%>
<% if (!isDefault) { %>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        LOAD_TRACKER.push('<%=key%>');
    });
</script>
</pwm:script>
<% } %>
<div id="titlePane_<%=key%>" class="setting_outline" <%if(isDefault){%> onclick="startEditor(this,'<%=key%>')" style="cursor:pointer;" <%}%>>
    <div class="setting_title" id="title_localeBundle-<%=bundleName%>-<%=key%>">
        <label id="label_<%=key%>" for="value_<%=key%>"><%=key%></label>
        <% if (isDefault) { %>
        <span id="editIcon_<%=key%>">
        <span class="fa fa-edit"></span>&nbsp;
        </span>
        <% } %>
        <div class="icon_button fa fa-undo" title="Reset" id="resetButton-localeBundle-<%=bundleName%>-<%=key%>" onclick="handleResetClick('localeBundle-<%=bundleName%>-<%=key%>')"
             style="visibility:hidden; vertical-align:bottom; float: right; cursor: pointer"></div>
    </div>
    <div class="setting_body" id="titlePane_localeBundle-<%=bundleName%>-<%=key%>">
        <table id="table_<%=key%>" style="border-width:0" width="500">
            <%
                if (isDefault) {
                    final String defaultValue = ResourceBundle.getBundle(bundleName.getTheClass().getName(),PwmConstants.DEFAULT_LOCALE).getString(key);
            %>
            <tr style="border-width:0">
                <td style="border-width:0">
                </td>
                <td style="border-width:0; width: 100%; color: #808080; margin: 2px">
                    <%= StringUtil.escapeHtml(defaultValue) %>
                </td>
            </tr>
            <%
                for(final Locale loopLocale : ContextManager.getPwmApplication(session).getConfig().getKnownLocales()) {
                    final String localizedValue = ResourceBundle.getBundle(bundleName.getTheClass().getName(),loopLocale).getString(key);
                    if (!defaultValue.equals(localizedValue)) {
            %>
            <tr style="border-width:0">
                <td style="border-width:0" id="localeKey_<%=key%>_<%=loopLocale.toString()%>">
                    <%= loopLocale.toString() %>
                </td>
                <td style="border-width:0; width: 100%; color: #808080; margin: 2px">
                    <%= StringUtil.escapeHtml(localizedValue) %>
                </td>
            </tr>
            <pwm:script>
            <script>
                PWM_GLOBAL['startupFunctions'].push(function() {
                    PWM_VAR['localeKey_tooltips'] = PWM_VAR['localeKey_tooltips'] || {};
                    PWM_VAR['localeKey_tooltips']['<%=loopLocale.toString()%>'] = PWM_VAR['localeKey_tooltips']['<%=loopLocale.toString()%>'] || new Array();
                    PWM_VAR['localeKey_tooltips']['<%=loopLocale.toString()%>'].push('localeKey_<%=key%>_<%=loopLocale.toString()%>');
                });
            </script>
            </pwm:script>
            <% } %>
            <% } %>
            <% } else { %>
            <tr style="border-width:0">
                <td style="border-width:0">
                    <pwm:display key="Display_PleaseWait"/>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
</div>
<br/>
<% } %>
</div>
<pwm:script>
<script type="text/javascript">
    function doLazyLoad(key) {
        var waitMsg = PWM_MAIN.getObject('waitMsg');
        if (waitMsg != null) {
            waitMsg.innerHTML = 'Loading display values.... ' + LOAD_TRACKER.length + " remaining.";
        }

        var settingKey = 'localeBundle-' + '<%=bundleName%>' + '-' + key;
        LocaleTableHandler.initLocaleTable('table_' + key, settingKey, '.*', 'LOCALIZED_TEXT_AREA');
        if (LOAD_TRACKER.length > 0) {
            setTimeout(function(){
                doLazyLoad(LOAD_TRACKER.pop());
            },100); // time between element reads
        } else {
            PWM_MAIN.closeWaitDialog();
            finishPageLoad();
        }
    }

    function initTooltips() {
        for (var localeKey in PWM_VAR['localeKey_tooltips']) {
            var connectIDs = PWM_VAR['localeKey_tooltips'][localeKey];
            var labelText = localeKey + ' - ' + PWM_GLOBAL['localeInfo'][localeKey];
            PWM_MAIN.showTooltip({
                id: connectIDs,
                position: ['below','above'],
                text: labelText
            });
        }
    }

    function startEditor(element,key) {
        doLazyLoad(key);
        element.onclick=null;
        element.style.cursor = 'auto';
        var editIconDiv = PWM_MAIN.getObject('editIcon_' + key);
        if (editIconDiv) {
            editIconDiv.parentElement.removeChild(editIconDiv);
        }
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        if (LOAD_TRACKER.length > 0) {
            PWM_MAIN.showWaitDialog({text:'<div id="waitMsg">Loading custom display values.......</div>',loadFunction:function() {
                LOAD_TRACKER.reverse();
                doLazyLoad(LOAD_TRACKER.pop());
            }});
        } else {
            finishPageLoad();
        }
        initTooltips();
    });

    function finishPageLoad() {
        PWM_MAIN.getObject('loadingDiv').style.display = 'none';
        PWM_MAIN.getObject('localeEditor_body').style.display = 'inline';
    }
</script>
</pwm:script>
