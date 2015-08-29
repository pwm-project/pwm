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
<%@ page import="password.pwm.RecoveryVerificationMethod" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="java.util.List" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html dir="<pwm:LocaleOrientation/>">
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <%
            final List<RecoveryVerificationMethod.UserPrompt> prompts = (List<RecoveryVerificationMethod.UserPrompt>)JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.ForgottenPasswordPrompts);
            final String instructions = (String)JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.ForgottenPasswordInstructions);
        %>
        <p><%=instructions%></p>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" autocomplete="off">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <% for (final RecoveryVerificationMethod.UserPrompt userPrompt : prompts) { %>
            <div class="formFieldLabel">
                <%= userPrompt.getDisplayPrompt() %>
            </div>

            <input type="password" id="remote-<%=userPrompt.getIdentifier()%>" name="remote-<%=userPrompt.getIdentifier()%>" class="inputfield passwordfield" required="required" autofocus/>
            <% } %>
            <div class="buttonbar">
                <button type="submit" class="btn" name="submitBtn" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <% if ("true".equals(JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ForgottenPasswordOptionalPageView))) { %>
                <button type="button" id="button-goBack" name="button-goBack" class="btn" >
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <%@ include file="/WEB-INF/jsp/fragment/forgottenpassword-cancel.jsp" %>
                <input type="hidden" id="processAction" name="processAction" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.enterRemoteResponse%>"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script>
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button-goBack','click',function() {
                PWM_MAIN.submitPostAction('<%=PwmServletDefinition.ForgottenPassword.servletUrlName()%>', '<%=ForgottenPasswordServlet.ForgottenPasswordAction.verificationChoice%>');
            });
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

