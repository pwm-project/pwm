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

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ page import="password.pwm.bean.servlet.ActivateUserBean" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html dir="<pwm:LocaleOrientation/>">
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <%
            final ActivateUserBean aub = PwmSession.getPwmSession(session).getActivateUserBean();
            String destination = aub.getTokenSendAddress();
        %>
        <p><pwm:Display key="Display_RecoverEnterCode" value1="<%=destination%>"/></p>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:Display key="Field_Code"/></label></h2>
        <div id="buttonbar">
            <form action="<pwm:url url='ActivateUser'/>" method="post"
              enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn',this);return false"
              style="display: inline;">
                <textarea style="height: 130px; width: 80%; resize: none; display: block; margin-bottom: 30px;" id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="inputfield"></textarea>
                <input type="submit" class="btn"
                       name="search"
                       value="<pwm:Display key="Button_CheckCode"/>"
                       id="submitBtn"/>
                <input type="reset" class="btn"
                       name="reset" onclick="clearForm('searchForm');return false;"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='ActivateUser'/>" method="post"
                  enctype="application/x-www-form-urlencoded"
                  style="display: inline;">
                <input type="hidden" name="processAction" value="reset"/>
                <input type="submit" name="button" class="btn"
                       value="<pwm:Display key="Button_Cancel"/>"
                       id="button_reset"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <% } %>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('<%=PwmConstants.PARAM_TOKEN%>').focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

