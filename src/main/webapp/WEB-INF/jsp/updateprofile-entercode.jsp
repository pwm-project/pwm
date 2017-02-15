<%@ page import="password.pwm.bean.TokenVerificationProgress" %>
<%@ page import="password.pwm.http.bean.NewUserBean" %>
<%@ page import="password.pwm.http.servlet.newuser.NewUserServlet" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.bean.UpdateProfileBean" %>
<%@ page import="password.pwm.config.profile.UpdateAttributesProfile" %>

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

<%
    final UpdateProfileBean updateProfileBean = JspUtility.getSessionBean(pageContext,UpdateProfileBean.class);
    final TokenVerificationProgress tokenVerificationProgress = updateProfileBean.getTokenVerificationProgress();
%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_UpdateProfile" displayIfMissing="true"/></div>
        <% if (updateProfileBean.getTokenVerificationProgress().getPhase() == TokenVerificationProgress.TokenChannel.EMAIL) { %>
        <p><pwm:display key="Display_UpdateProfileEnterCode" value1="<%=tokenVerificationProgress.getTokenDisplayText()%>"/></p>
        <% } else if (updateProfileBean.getTokenVerificationProgress().getPhase() == TokenVerificationProgress.TokenChannel.SMS) { %>
        <p><pwm:display key="Display_UpdateProfileEnterCodeSMS" value1="<%=tokenVerificationProgress.getTokenDisplayText()%>"/></p>
        <% } %>
        <form action="<pwm:current-url/>" method="post" autocomplete="off" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
            <%@ include file="fragment/message.jsp" %>
            <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:display key="Field_Code"/></label></h2>
            <textarea id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" <pwm:autofocus/> class="tokenInput"></textarea>
            <div class="buttonbar">
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>

                <button id="button-reset" type="submit" class="btn" name="button" form="form-reset">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
            </div>
        </form>
        <form id="form-reset" name="form-reset" method="post" name="form-cancel" enctype="application/x-www-form-urlencoded"
              class="pwm-form">
            <input type="hidden" name="processAction" value="unConfirm"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script> <%-- ie doesn't support 'from' attribute on buttons, so handle with this script --%>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            PWM_MAIN.addEventHandler('button-reset', 'click', function(e){
                console.log('intercepted cancel button');
                PWM_MAIN.cancelEvent(e);
                var cancelForm = PWM_MAIN.getObject('form-reset');
                PWM_MAIN.handleFormSubmit(cancelForm);
            });
        });
    </script>
</pwm:script>

<%@ include file="fragment/footer.jsp" %>
</body>
</html>

