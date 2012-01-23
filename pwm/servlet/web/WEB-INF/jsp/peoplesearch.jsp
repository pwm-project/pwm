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

<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.servlet.PeopleSearchServlet" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<html xmlns="http://www.w3.org/1999/xhtml" dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();getObject('username').focus();" class="tundra">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody">
        <form action="<pwm:url url='PeopleSearch'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();">
            <%@ include file="fragment/message.jsp" %>
            <p>&nbsp;</p>

            <% //check to see if any locations are configured.
                if (!ContextManager.getPwmApplication(session).getConfig().getLoginContexts().isEmpty()) {
            %>
            <h2><label for="context"><pwm:Display key="Field_Location"/></label></h2>
            <select name="context">
                <pwm:DisplayLocationOptions name="context"/>
            </select>
            <% } %>
            <p><pwm:Display key="Display_PeopleSearch"/></p>
            <input tabindex="1" type="search" id="username" name="username" class="inputfield"
                   value="<pwm:ParamValue name='username'/>"/>
            <input tabindex="3" type="submit" class="btn"
                   name="search"
                   value="<pwm:Display key="Button_Search"/>"
                   id="submitBtn"/>

            <input type="hidden"
                   name="processAction"
                   value="search"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <br class="clear"/>
        <% final PeopleSearchServlet.PeopleSearchResults searchResults = (PeopleSearchServlet.PeopleSearchResults)request.getAttribute("searchResults"); %>
        <% if (searchResults != null) { %>
        <table>
            <tr>
                <% for (final String keyName : searchResults.getHeaders()) { %>
                <td class="key" style="text-align: left;">
                    <%=keyName%>
                </td>
                <% } %>
            </tr>
            <% for (final String userDN: searchResults.getResults().keySet()) { %>
            <tr onclick="alert('selected: ' + <%=userDN%>)" onmouseover="">
                <% for (final String attribute : searchResults.getAttributes()) { %>
                <% final String value = searchResults.getResults().get(userDN).get(attribute); %>
                <td id="userDN-<%=userDN%>" onclick="alert('selected: ' + <%=userDN%>)" >
                    <%= value == null || value.length() < 1 ? "&nbsp;" : value %>
                </td>
                <% } %>
            </tr>
            <% } %>
        </table>
        <% } %>
    </div>
    <br class="clear"/>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
