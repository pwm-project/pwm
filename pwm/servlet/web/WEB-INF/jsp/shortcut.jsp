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

<%@ page import="password.pwm.config.ShortcutItem" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<%
    final boolean newWindow = JspUtility.getPwmRequest(pageContext).getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_NEW_WINDOW);
    Map<String, ShortcutItem> shortcutItems = Collections.emptyMap();
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        shortcutItems = pwmRequest.getPwmSession().getSessionStateBean().getVisibleShortcutItems();
    } catch (PwmException e) {
        /* noop */
    }
%>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Shortcuts"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_Shortcuts"/></p>
        <%@ include file="fragment/message.jsp" %>
        <% if (shortcutItems.isEmpty()) { %>
        <p>No shortcuts</p>
        <% } else { %>
        <table class="noborder">
            <% for (final ShortcutItem item : shortcutItems.values()) { %>
            <tr>
                <td class="menubutton_key">
                    <form action="<pwm:current-url/>" method="post" name="form-shortcuts-<%=item%>" enctype="application/x-www-form-urlencoded" id="form-shortcuts-<%=item%>" <%=newWindow ? " target=\"_blank\"" : ""%>>
                        <input type="hidden" name="processAction" value="selectShortcut">
                        <input type="hidden" name="link" value="<%=item.getLabel()%>">
                        <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                        <button type="submit" class="menubutton">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-external-link"></span></pwm:if>
                            <%=item.getLabel()%>
                        </button>
                    </form>
                </td>
                <td>
                    <p><%= item.getDescription() %></p>
                </td>
            </tr>
            <% } %>
        </table>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

