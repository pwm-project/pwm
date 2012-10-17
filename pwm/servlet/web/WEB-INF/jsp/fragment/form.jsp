<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.util.List" %>

<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmSession pwmSession = PwmSession.getPwmSession(session);
    final SessionStateBean ssBean = pwmSession.getSessionStateBean();
    List<FormConfiguration> formConfigurationList = ContextManager.getPwmApplication(session).getConfig().readSettingAsForm((PwmSetting)request.getAttribute("form"));
    for (FormConfiguration loopConfiguration : formConfigurationList) {
        final String currentValue = StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty(loopConfiguration.getName(),""));

%>
<% if (loopConfiguration.getType().equals(FormConfiguration.Type.hidden)) { %>
<input style="text-align: left;" id="<%=loopConfiguration.getName()%>" type="hidden" class="inputfield"
       name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"/>
<% } else { %>
<h1>
    <label for="<%=loopConfiguration.getName()%>"><%= loopConfiguration.getLabel(ssBean.getLocale()) %></label>
</h1>
<% boolean isReadonly = loopConfiguration.isReadonly() || "true".equalsIgnoreCase((String)request.getAttribute("form-readonly")); %>
<% if (loopConfiguration.getType() == FormConfiguration.Type.select) { %>
<select id="<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" class="inputfield">
    <% for (final String optionName : loopConfiguration.getSelectOptions().keySet()) {%>
    <option value="<%=optionName%>" <%if(optionName.equals(currentValue)){%>selected="selected"<%}%>>
        <%=loopConfiguration.getSelectOptions().get(optionName)%>
    </option>
    <% } %>
</select>
<% } else { %>
<input style="text-align: left;" id="<%=loopConfiguration.getName()%>" type="<%=loopConfiguration.getType()%>" class="inputfield"
       name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"
        <%if(loopConfiguration.getPlaceholder()!=null){%> placeholder="<%=loopConfiguration.getPlaceholder()%>"<%}%>
        <%if(isReadonly){%> required="required"<%}%>
        <%if(loopConfiguration.isReadonly()){%> readonly="readonly"<%}%>
       maxlength="<%=loopConfiguration.getMaximumLength()%>"/>
<% if (loopConfiguration.isConfirmationRequired()) { %>
<h1>
    <label id="<%=loopConfiguration.getName()%>_confirm"><pwm:Display key="Field_Confirm_Prefix"/> <%= loopConfiguration.getLabel(ssBean.getLocale()) %></label>
</h1>
<input style="" id="<%=loopConfiguration.getName()%>_confirm" type="<%=loopConfiguration.getType()%>" class="inputfield"
       name="<%=loopConfiguration.getName()%>_confirm"
        <%if(loopConfiguration.getPlaceholder()!=null){%> placeholder="<%=loopConfiguration.getPlaceholder()%>"<%}%>
        <%if(loopConfiguration.isRequired()){%> required="required"<%}%>
        <%if(loopConfiguration.isReadonly()){%> readonly="readonly"<%}%>
       maxlength="<%=loopConfiguration.getMaximumLength()%>"/>
<% } %>
<% } %>
<% } %>
<% if (loopConfiguration.getJavascript() != null && loopConfiguration.getJavascript().length() > 0) { %>
<script type="text/javascript">
    try {
        <%=loopConfiguration.getJavascript()%>
    } catch (e) {
        console.log('error executing custom javascript for form field \'' + <%=loopConfiguration.getName()%> + '\', error: ' + e)
    }
</script>
<% } %>
<% } %>

<% if ("true".equalsIgnoreCase((String)request.getAttribute("form_showPasswordFields"))) { %>
<h1>
    <label for="password1"><pwm:Display key="Field_NewPassword"/></label>
</h1>
<input type="password" name="password1" id="password1" class="inputfield" onkeypress="getObject('password2').value=''"/>

<h1><label for="password2"><pwm:Display key="Field_ConfirmPassword"/></label></h1>
<input type="password" name="password2" id="password2" class="inputfield"/>
<% } %>
