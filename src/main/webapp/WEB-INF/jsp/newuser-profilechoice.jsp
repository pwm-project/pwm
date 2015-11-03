<%@ page import="password.pwm.config.profile.NewUserProfile" %>
<%@ page import="password.pwm.http.servlet.NewUserServlet" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="java.util.Map" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
    final Map<String,NewUserProfile> newUserProfiles = pwmRequest.getConfig().getNewUserProfiles();
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_NewUser"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>
            <pwm:display key="Display_NewUserProfile"/>
        </p>
        <table class="noborder">
            <colgroup>

            </colgroup>
            <% for (final NewUserProfile profile : newUserProfiles.values()) { %>
            <tr>
                <td>
                    <form action="<pwm:current-url/>" method="post" class="pwm-form"
                          enctype="application/x-www-form-urlencoded" name="search">
                        <button class="btn" type="submit" name="submitBtn">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                            <%=profile.getDisplayName(pwmRequest.getLocale())%>
                        </button>
                        <input type="hidden" name="profile" value="<%=profile.getIdentifier()%>"/>
                        <input type="hidden" name="processAction" value="<%=NewUserServlet.NewUserAction.profileChoice%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
            </tr>
            <% } %>
        </table>
        <br/>
        <div class="buttonbar">
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <form action="<pwm:url url='<%=PwmServletDefinition.Command.servletUrl()%>' addContext="true"/>" method="get"
                  enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
                <button class="btn" type="submit" name="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-times"></span></pwm:if>
                    <pwm:display key="Button_Cancel"/>
                </button>
                <input type="hidden" name="processAction" value="continue"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <% } %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.getObject('username').focus();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

