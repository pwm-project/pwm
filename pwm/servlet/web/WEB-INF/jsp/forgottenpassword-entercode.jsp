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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ page import="password.pwm.bean.ForgottenPasswordBean" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<body onload="pwmPageLoadHandler();getObject('code').focus();" class="tundra">
<div id="wrapper">
    <jsp:include page="header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <% 
           final ForgottenPasswordBean fpb = PwmSession.getPwmSession(session).getForgottenPasswordBean();
           final String destMail = fpb.getTokenEmailAddress();
           final String destSms = fpb.getTokenSmsNumber();
           String destination = "";
           if (fpb.getEmailUsed()) {
             destination += destMail;
           }
           if (fpb.getSmsUsed()) {
             if (destination.length() > 0) {
               destination += " / ";
             }
             destination += destSms;
           }
        %>
        <p><pwm:Display key="Display_RecoverEnterCode" value1="<%=destination%>"/></p>

        <form action="<pwm:url url='../public/ForgottenPassword'/>" method="post"
              enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn',this);return false" onreset="handleFormClear();return false">
            <% //check to see if there is an error
                if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
            %>
            <span id="error_msg" class="msg-error">
                <pwm:ErrorMessage/>
            </span>
            <% } %>
            <h2><label for="code">Code</label></h2>
            <input type="text" id="code" name="code" class="inputfield"/>

            <div id="buttonbar">
                <input type="submit" class="btn"
                       name="search"
                       value="     <pwm:Display key="Button_CheckCode"/>     "
                       id="submitBtn"/>
                <input type="reset" class="btn"
                       name="reset"
                       value="     <pwm:Display key="Button_Reset"/>     "/>
                <input type="hidden" id="processAction" name="processAction" value="forgottenCode"/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                        onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                    &nbsp;&nbsp;&nbsp;<pwm:Display key="Button_Cancel"/>&nbsp;&nbsp;&nbsp;
                </button>
                <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <br class="clear"/>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>

