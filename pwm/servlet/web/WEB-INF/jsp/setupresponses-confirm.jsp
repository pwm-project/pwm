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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.servlet.SetupResponsesBean" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupResponsesBean responseBean = PwmSession.getPwmSession(session).getSetupResponseBean(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ConfirmResponses"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_ConfirmResponses"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <%
            for (final Challenge loopChallenge : responseBean.getResponseData().getResponseMap().keySet()) {
                final String responseText = responseBean.getResponseData().getResponseMap().get(loopChallenge);
        %>
        <h2><%= StringEscapeUtils.escapeHtml(loopChallenge.getChallengeText()) %>
        </h2>

        <p>
            &nbsp;<%="\u00bb"%>&nbsp;
            <%= StringEscapeUtils.escapeHtml(responseText) %>
        </p>
        <% } %>
        <br/>
        <%
            for (final Challenge loopChallenge : responseBean.getHelpdeskResponseData().getResponseMap().keySet()) {
                final String responseText = responseBean.getHelpdeskResponseData().getResponseMap().get(loopChallenge);
        %>
        <h2><%= StringEscapeUtils.escapeHtml(loopChallenge.getChallengeText()) %>
        </h2>

        <p>
            &nbsp;<%="\u00bb"%>&nbsp;
            <%= StringEscapeUtils.escapeHtml(responseText) %>
        </p>
        <% } %>
        <br/>
        <div id="buttonbar">
            <form style="display: inline" action="<pwm:url url='SetupResponses'/>" method="post" name="changeResponses"
                  enctype="application/x-www-form-urlencoded"
                  onsubmit="handleFormSubmit('confirm_btn',this);return false">
                <input type="submit" name="confirm_btn" class="btn" id="confirm_btn"
                       value="<pwm:Display key="Button_ConfirmResponses"/>"/>
                <input type="hidden" name="processAction" value="confirmResponses"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form style="display: inline" action="<pwm:url url='SetupResponses'/>" method="post" name="confirmResponses"
                  enctype="application/x-www-form-urlencoded"
                  onsubmit="handleFormSubmit('change_btn',this);return false">
                <input type="submit" name="change_btn" class="btn" id="change_btn"
                       value="<pwm:Display key="Button_ChangeResponses"/>"/>
                <input type="hidden" name="processAction" value="changeResponses"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
