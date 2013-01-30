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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();try {document.forms.verifyCaptcha.recaptcha_response_field.focus()} catch (e) {}"
      class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Captcha"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_Captcha"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>

        <form action="<pwm:url url='Captcha'/>" method="post" enctype="application/x-www-form-urlencoded"
              name="verifyCaptcha" onsubmit="handleFormSubmit('verify_button',this);return false">
            <%-- begin reCaptcha section (http://code.google.com/apis/recaptcha/docs/display.html) --%>
            <% final String reCaptchaPublicKey = ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.RECAPTCHA_KEY_PUBLIC); %>
            <% final String reCaptchaProtocol = request.isSecure() ? "https" : "http"; %>
            <script type="text/javascript">
                var RecaptchaOptions = { theme : 'white' };
            </script>
            <div style="margin-left: auto; margin-right: auto; width: 322px">
                <script type="text/javascript"
                        src="<%=reCaptchaProtocol%>://www.google.com/recaptcha/api/challenge?k=<%=reCaptchaPublicKey%>">
                </script>
            </div>
            <noscript>
                <iframe src="<%=reCaptchaProtocol%>://www.google.com/recaptcha/api/noscript?k=<%=reCaptchaProtocol%>"
                        height="300" width="500" frameborder="0"></iframe>
                <br>
                <textarea name="recaptcha_challenge_field" rows="3" cols="40">
                </textarea>
                <input type="hidden" name="recaptcha_response_field"
                       value="manual_challenge">
            </noscript>
            <div id="buttonbar">
                <input type="hidden" name="processAction" value="doVerify"/>
                <input type="submit" name="verify" class="btn"
                       id="verify_button"
                       value="<pwm:Display key="Button_Verify"/>"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
            </div>
        </form>
    </div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
