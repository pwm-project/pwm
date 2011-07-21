<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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

<%@ page import="java.util.Collections" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getLocaleBundle(); %>
<% final ResourceBundle bundle = ResourceBundle.getBundle(bundleName.getTheClass().getName()); %>
<h1 style="text-align:center;"><%=bundleName.getTheClass().getSimpleName()%></h1>
<p>Edit the display fields presented to users.  Whenever a single value is modified for a setting, all values for that setting will be used to override all default locale-specific values for that particular setting.  Display keys not modified from the default will use the default display
    value of the current pwm defaults.</p>
<script type="text/javascript">
    showError('Loading page...');
    var LOAD_TRACKER = new Array();
</script>
<% for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) { %>
<div id="titlePane_<%=key%>" style="margin-top:0; padding-top:0; border-top:0">
    <div class="msg-info" style="width: 580px; font-weight: bolder; font-family: Trebuchet MS,sans-serif">
        <label id="label_<%=key%>" for="value_<%=key%>"><%=key%></label>
        <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
             id="resetButton-localeBundle-<%=bundleName%>-<%=key%>"
             style="visibility:hidden; vertical-align:bottom; float: right"
             onclick="handleResetClick('localeBundle-<%=bundleName%>-<%=key%>')"/>
    </div>
    <div class="msg-info" style="width: 580px; background: white;">
        <table id="table_<%=key%>" style="border-width:0" width="500">
            <tr style="border-width:0">
                <td style="border-width:0">
                    [Loading...]
                </td>
            </tr>
        </table>
    </div>
</div>
<br/>
<script type="text/javascript">
    LOAD_TRACKER.push('<%=key%>');
</script>
<% } %>
<script type="text/javascript">
    function doLazyLoad(key) {
        var errorMsgObj = getObject("error_msg");
        errorMsgObj.firstChild.nodeValue = 'Loading display elements.... ' + LOAD_TRACKER.length;

        var settingKey = 'localeBundle-' + '<%=bundleName%>' + '-' + key;
        initLocaleTable('table_' + key, settingKey, '.*', 'LOCALIZED_TEXT_AREA');
        if (LOAD_TRACKER.length > 0) {
            setTimeout(function(){
                doLazyLoad(LOAD_TRACKER.pop());
            },100);
        } else {
            clearError();
        }
    }
    LOAD_TRACKER.reverse();
    doLazyLoad(LOAD_TRACKER.pop());
</script>

