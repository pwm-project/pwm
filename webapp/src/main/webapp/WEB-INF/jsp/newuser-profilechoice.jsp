<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.config.profile.NewUserProfile" %>
<%@ page import="password.pwm.http.servlet.newuser.NewUserServlet" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.http.servlet.command.CommandServlet" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = JspUtility.getPwmRequest( pageContext );
    final Map<String,String> newUserProfiles = (Map)pwmRequest.getAttribute(PwmRequestAttribute.NewUser_VisibleProfiles);
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_NewUser"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_NewUser" displayIfMissing="true"/></h1>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>
            <pwm:display key="Display_NewUserProfile"/>
        </p>
        <table class="noborder">
            <colgroup>

            </colgroup>
            <% for (final String profileID : newUserProfiles.values()) { %>
            <tr>
                <td>
                    <form action="<pwm:current-url/>" method="post" class="pwm-form"
                          enctype="application/x-www-form-urlencoded" name="search">
                        <button class="btn" type="submit" name="submitBtn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                            <%=newUserProfiles.get(profileID)%>
                        </button>
                        <input type="hidden" name="profile" value="<%=profileID%>"/>
                        <input type="hidden" name="processAction" value="<%=NewUserServlet.NewUserAction.profileChoice%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
            </tr>
            <% } %>
        </table>
        <br/>
        <div class="buttonbar">
            <% if (JspUtility.getPwmRequest( pageContext ).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <form action="<pwm:url url='<%=PwmServletDefinition.PublicCommand.servletUrl()%>' addContext="true"/>" method="get"
                  enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
                <button class="btn" type="submit" name="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                    <pwm:display key="Button_Cancel"/>
                </button>
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=CommandServlet.CommandAction.next.toString()%>"/>
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

