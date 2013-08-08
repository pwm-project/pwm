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

<%@ page import="com.novell.ldapchai.cr.ChallengeSet" %>
<%@ page import="password.pwm.bean.servlet.SetupResponsesBean" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final SetupResponsesBean responseBean = PwmSession.getPwmSession(session).getSetupResponseBean();
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/responses.js'/>"></script>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupResponses"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_SetupHelpdeskResponses"/></p>
        <form action="<pwm:url url='SetupResponses'/>" method="post" name="setupResponses"
              enctype="application/x-www-form-urlencoded" id="setupResponses"
              onsubmit="handleFormSubmit('setresponses_button',this);return false">
            <%@ include file="fragment/message.jsp" %>
            <% request.setAttribute("setupData",responseBean.getHelpdeskResponseData()); %>
            <script type="text/javascript">PWM_GLOBAL['responseMode'] = "helpdesk";</script>
            <jsp:include page="fragment/setupresponses-form.jsp"/>
            <div id="buttonbar">
                <input type="hidden" name="processAction" value="setHelpdeskResponses"/>
                <input type="submit" name="setResponses" class="btn" id="setresponses_button"
                       value="<pwm:Display key="Button_SetResponses"/>"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        startupResponsesPage();
        document.forms[0].elements[0].focus();
        ShowHidePasswordHandler.initAllForms();
    });
</script>

<%@ include file="fragment/footer.jsp" %>
</body>
</html>
