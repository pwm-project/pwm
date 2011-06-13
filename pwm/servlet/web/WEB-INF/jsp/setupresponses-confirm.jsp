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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.SetupResponsesBean" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupResponsesBean responseBean = PwmSession.getPwmSession(session).getSetupResponseBean(); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<body onload="pwmPageLoadHandler();startupResponsesPage()(false); document.forms.setupResponses.elements[0].focus();"
      class="tundra">
<div id="wrapper">
    <jsp:include page="header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ConfirmResponses"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_ConfirmResponses"/></p>
        <% // if there is an error, then always show the error block if javascript is enabled.  Otherwise, only show
            // the error block if javascript is available (for ajax use).
            if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
        %>
        <span id="error_msg" class="msg-error"><pwm:ErrorMessage/>&nbsp;</span>
        <% } %>
        <br/>
        <%
            for (final Challenge loopChallenge : responseBean.getResponseMap().keySet()) {
                final String responseText = responseBean.getResponseMap().get(loopChallenge);
        %>
        <h2><%= StringEscapeUtils.escapeHtml(loopChallenge.getChallengeText()) %>
        </h2>

        <p>
            &nbsp;»&nbsp;
            <%= StringEscapeUtils.escapeHtml(responseText) %>
        </p>
        <% } %>
        <br/>

        <div id="buttonbar">
            <form action="<pwm:url url='SetupResponses'/>" method="post" name="changeResponses"
                  enctype="application/x-www-form-urlencoded">
                <input type="submit" name="change_btn" class="btn"
                       value="    « <pwm:Display key="Button_ChangeResponses"/> «    "/>
                <input type="hidden" name="processAction" value="changeResponses"/>
            </form>
            <br/>

            <form action="<pwm:url url='SetupResponses'/>" method="post" name="confirmResponses"
                  enctype="application/x-www-form-urlencoded">
                <input type="submit" name="confirm_btn" class="btn"
                       value="    <pwm:Display key="Button_ConfirmResponses"/>    "/>
                <input type="hidden" name="processAction" value="confirmResponses"/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                        onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                    &nbsp;&nbsp;&nbsp;<pwm:Display key="Button_Cancel"/>&nbsp;&nbsp;&nbsp;
                </button>
                <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <br class="clear"/>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
