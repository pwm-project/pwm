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

<%@ page import="password.pwm.bean.servlet.PeopleSearchBean" %>
<%@ page import="password.pwm.util.operations.UserSearchEngine" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final PeopleSearchBean peopleSearchBean = (PeopleSearchBean)pwmSession.getSessionBean(PeopleSearchBean.class); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody">
        <form action="<pwm:url url='PeopleSearch'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn');">
            <%@ include file="fragment/message.jsp" %>
            <p>&nbsp;</p>

            <p><pwm:Display key="Display_PeopleSearch"/></p>
            <input type="search" id="username" name="username" class="inputfield"
                   value="<%=peopleSearchBean.getSearchString()!=null?peopleSearchBean.getSearchString():""%>" autofocus/>
            <input type="submit" class="btn"
                   name="search"
                   value="<pwm:Display key="Button_Search"/>"
                   id="submitBtn"/>
            <input type="hidden"
                   name="processAction"
                   value="search"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                    onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                <pwm:Display key="Button_Cancel"/>
            </button>
            <% } %>
        </form>
        <br class="clear"/>
        <% final UserSearchEngine.UserSearchResults searchDetails = peopleSearchBean.getSearchDetails(); %>
        <% if (searchDetails != null && !searchDetails.getResults().isEmpty()) { %>
        <% final String userDN = searchDetails.getResults().keySet().iterator().next(); %>
        <table>
            <% for (final String attribute : searchDetails.getHeaderAttributeMap().keySet()) { %>
            <% final String header = searchDetails.getHeaderAttributeMap().get(attribute); %>
            <% if (searchDetails.getResults().get(userDN).containsKey(attribute)) { %>
            <tr>
                <td class="key" style="text-align: right; white-space: nowrap;">
                    <%=header%>
                </td>
                <td>
                    <%=searchDetails.getResults().get(userDN).get(attribute)%>
                </td>
            </tr>
            <% } %>
            <% } %>
        </table>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('username').focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
