<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.http.servlet.ShortcutServlet" %>
<%@ page import="password.pwm.util.StringUtil" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<%
    final boolean newWindow = JspUtility.getPwmRequest(pageContext).getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_NEW_WINDOW);
    final List<ShortcutItem> shortcutItems = (List<ShortcutItem>)JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ShortcutItems);
%>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Shortcuts"/>
    </jsp:include>
    <div id="centerbody" class="tile-centerbody">
        <div id="page-content-title"><pwm:display key="Title_Shortcuts" displayIfMissing="true"/></div>
        <% if (shortcutItems.isEmpty()) { %>
        <p>No shortcuts</p>
        <% } else { %>
        <% for (final ShortcutItem item : shortcutItems) { %>
        <a href="<pwm:current-url/>?processAction=<%=ShortcutServlet.ShortcutAction.selectShortcut%>&link=<%=StringUtil.escapeHtml(item.getLabel())%>&pwmFormID=<pwm:FormID/>" id="form-shortcuts-<%=StringUtil.escapeHtml(item.getLabel())%>" <%=newWindow ? " target=\"_blank\"" : ""%>>
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-external-link"></span></pwm:if>
                    </div>
                    <div class="tile-title" title="<%=item.getLabel()%>"><%=item.getLabel()%></div>
                    <div class="tile-subtitle" title="<%=item.getDescription()%>"><%=item.getDescription()%></div>
                </div>
            </div>
        </a>
        <% } %>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

