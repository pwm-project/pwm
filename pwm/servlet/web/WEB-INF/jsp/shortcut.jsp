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

<%@ page import="password.pwm.config.ShortcutItem" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<% final Map<String, ShortcutItem> shortcutItems = PwmSession.getPwmSession(session).getSessionStateBean().getVisibleShortcutItems(); %>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Shortcuts"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_Shortcuts"/></p>
        <%@ include file="fragment/message.jsp" %>
        <% if (shortcutItems.isEmpty()) { %>
        <p>No shortcuts</p>
        <% } else { %>
        <table style="border:0">
            <% for (final ShortcutItem item : shortcutItems.values()) { %>
            <tr style="border:0">
                <td style="border:0; text-align: right; width:10%">
                    <a onclick="showWaitDialog()" class="menubutton" href="<%=request.getContextPath()%>/private/<pwm:url url='Shortcuts'/>?processAction=selectShortcut&link=<%= item.getLabel() %>">
                        <%= item.getLabel() %>
                    </a>
                </td>
                <td style="border: 0">
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

