<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2010 The PWM Project
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

<%@ page import="password.pwm.servlet.ConfigManagerServlet" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getLocaleBundle(); %>
<% final ResourceBundle bundle = ResourceBundle.getBundle(bundleName.getTheClass().getName()); %>
<h1 style="text-align:center;"><%=bundleName.getTheClass().getSimpleName()%></h1>
<script type="text/javascript">
    showError('');
    LOAD_TRACKER = new Array();
    function doLazyLoad(key) {
        if (LOAD_TRACKER[key]) {
            return;
        }
        var settingKey = 'localeBundle-' + '<%=bundleName%>' + '-' + key;
        initLocaleTable('table_' + key, settingKey, '.*', 'LOCALIZED_TEXT_AREA');
        LOAD_TRACKER[key] = true;
    }
</script>
<% int delayTimer = 1000; %>
<% for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) { %>
<div id="titlePane_<%=key%>" style="margin-top:0; padding-top:0; border-top:0" on="doLazyLoad('<%=key%>')">
    <h2><label id="label_<%=key%>" for="value_<%=key%>"><%=key%></label></h2>
    <table id="table_<%=key%>" style="border-width:0" width="500">
        <tr style="border-width:0">
            <td style="border-width:0"><input type="text" disabled="disabled" value="[Loading...]" style="width: 500px"/></td>
        </tr>
    </table>
</div>
<br/>
<script type="text/javascript">
    setTimeout(function(){
        doLazyLoad('<%=key%>');
    },<%=delayTimer += 200 %>);
</script>
<% } %>
