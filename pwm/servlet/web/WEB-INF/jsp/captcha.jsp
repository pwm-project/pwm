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
        function recaptchaCallback() {
            console.log('captcha completed, submitting form');
            PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('verifyCaptcha'));
        }
    </script>
</pwm:script>
<pwm:script-ref url="<%=(String)JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.CaptchaClientUrl)%>"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Captcha"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_Captcha"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='Captcha'/>" method="post" enctype="application/x-www-form-urlencoded" id="verifyCaptcha" name="verifyCaptcha" class="pwm-form">
            <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            <center>
                <div data-callback="recaptchaCallback" style="margin: auto !important" class="g-recaptcha" data-sitekey="<%=JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.CaptchaPublicKey)%>"></div>
            </center>
            <%--
            <noscript>
                <div style="width: 302px; height: 352px;">
                    <div style="width: 302px; height: 352px; position: relative;">
                        <div style="width: 302px; height: 352px; position: absolute;">
                            <iframe src="https://www.google.com/recaptcha/api/fallback?k=your_site_key"
                                    frameborder="0" scrolling="no"
                                    style="width: 302px; height:352px; border-style: none;">
                            </iframe>
                        </div>
                        <div style="width: 250px; height: 80px; position: absolute; border-style: none; bottom: 21px; left: 25px; margin: 0px; padding: 0px; right: 25px;">
                            <textarea id="g-recaptcha-response" name="g-recaptcha-response" class="g-recaptcha-response" style="width: 250px; height: 80px; border: 1px solid #c1c1c1; margin: 0px; padding: 0px; resize: none;" value="">
                            </textarea>
                        </div>
                    </div>
                </div>
            </noscript>
            --%>
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
