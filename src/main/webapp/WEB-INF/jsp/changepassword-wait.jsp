<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.bean.ChangePasswordBean" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.util.Date" %>
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

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS);%>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT);%>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<%
    long maxWaitSeconds = 30;
    long checkIntervalSeconds = 3;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        final ChangePasswordBean changePasswordBean = JspUtility.getSessionBean(pageContext, ChangePasswordBean.class);
        final Date maxCompleteTime = changePasswordBean.getChangePasswordMaxCompletion();
        maxWaitSeconds = maxCompleteTime == null ? 30 : TimeDuration.fromCurrent(maxCompleteTime).getTotalSeconds();
        checkIntervalSeconds = Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_AJAX_PW_WAIT_CHECK_SECONDS));
    } catch (PwmException e) {
        JspUtility.logError(pageContext,"error during page setup: " + e.getMessage());
    }
%>
<meta http-equiv="refresh" content="<%=maxWaitSeconds%>;url='ChangePassword?processAction=complete&pwmFormID=<pwm:FormID/>">
<noscript>
    <meta http-equiv="refresh" content="<%=checkIntervalSeconds%>;url='ChangePassword?processAction=complete&pwmFormID=<pwm:FormID/>">
</noscript>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PleaseWait"/>
    </jsp:include>
    <div id="centerbody" >
        <div id="page-content-title"><pwm:display key="Title_PleaseWait" displayIfMissing="true"/></div>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p><pwm:display key="Display_PleaseWaitPassword"/></p>
        <div class="meteredProgressBar">
          <progress id="html5ProgressBar" max="100" value="0">
              <div data-dojo-type="dijit/ProgressBar" style="width:100%" data-dojo-id="passwordProgressBar" id="passwordProgressBar" data-dojo-props="maximum:100"></div>
          </progress>
        </div>
        <div style="text-align: center; width: 100%; padding-top: 50px">
            <%--
            <div>Elapsed Time: <span id="elapsedSeconds"></span></div>
            <div>Estimated Time Remaining: <span id="estimatedRemainingSeconds"></span></div>
            --%>
        </div>
        <br/>
        <table id="progressMessageTable" style="padding-bottom: 20px; width: 80%; margin-right: 10%; margin-left: 10%">
        </table>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser", "dijit/ProgressBar","dojo/ready"], function(parser){
            parser.parse();
            PWM_CHANGEPW.refreshCreateStatus(<%=checkIntervalSeconds * 1000%>);
        });
    });
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/changepassword.js"/>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
