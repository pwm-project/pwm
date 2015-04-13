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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<%-- begin reCaptcha section (http://code.google.com/apis/recaptcha/docs/display.html) --%>
<pwm:script>
    <script type="text/javascript">
        function onloadCallback() {
            var recaptchaCallback = function() {
                console.log('captcha completed, submitting form');
                PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('verifyCaptcha'));
            };

            console.log('reached google recaptcha onload callback');
            PWM_MAIN.setStyle('captcha-loading','display','none');
            grecaptcha.render('recaptcha-container',{callback:recaptchaCallback,sitekey:'<%=JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.CaptchaPublicKey)%>'});
        }
    </script>
</pwm:script>
<script nonce="<pwm:value name="cspNonce"/>" src="<%=(String)JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.CaptchaClientUrl)%>?onload=onloadCallback&render=explicit" defer async></script>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Captcha"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_Captcha"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <div id="captcha-loading" class="WaitDialogBlank"></div>
        <form action="<pwm:url url='Captcha'/>" method="post" enctype="application/x-www-form-urlencoded" id="verifyCaptcha" name="verifyCaptcha" class="pwm-form">
            <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>

            <center>
                <div id="recaptcha-container">
                </div>
            </center>
            <noscript>
                <span><pwm:display key="Display_JavascriptRequired"/></span>
                <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
            </noscript>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="doVerify"/>
                <button type="submit" name="verify" class="btn" id="verify_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                    <pwm:display key="Button_Verify"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            try {
                document.forms.verifyCaptcha.recaptcha_response_field.focus()
            } catch (e) {
                /* noop */
            }
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
