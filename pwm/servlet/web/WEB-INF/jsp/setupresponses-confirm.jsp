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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.http.bean.SetupResponsesBean" %>
<%@ page import="password.pwm.util.StringUtil" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupResponsesBean responseBean = (SetupResponsesBean)JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ModuleBean); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ConfirmResponses"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_ConfirmResponses"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <%
            for (final Challenge loopChallenge : responseBean.getResponseData().getResponseMap().keySet()) {
                final String responseText = responseBean.getResponseData().getResponseMap().get(loopChallenge);
        %>
        <h2><%= StringUtil.escapeHtml(loopChallenge.getChallengeText()) %>
        </h2>

        <p>
            <span class="fa fa-chevron-circle-right"></span>
            <%= StringUtil.escapeHtml(responseText) %>
        </p>
        <% } %>
        <br/>
        <%
            for (final Challenge loopChallenge : responseBean.getHelpdeskResponseData().getResponseMap().keySet()) {
                final String responseText = responseBean.getHelpdeskResponseData().getResponseMap().get(loopChallenge);
        %>
        <h2><%= StringUtil.escapeHtml(loopChallenge.getChallengeText()) %>
        </h2>

        <p>
            <span class="fa fa-chevron-circle-right"></span>
            <%= StringUtil.escapeHtml(responseText) %>
        </p>
        <% } %>
        <br/>
        <div class="buttonbar">
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="changeResponses"
                  enctype="application/x-www-form-urlencoded" class="pwm-form">
                <button type="submit" name="confirm_btn" class="btn" id="confirm_btn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                    <pwm:display key="Button_ConfirmResponses"/>
                </button>
                <input type="hidden" name="processAction" value="confirmResponses"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="confirmResponses"
                  enctype="application/x-www-form-urlencoded" class="pwm-form">
                <button type="submit" name="change_btn" class="btn" id="change_btn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <input type="hidden" name="processAction" value="changeResponses"/>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
