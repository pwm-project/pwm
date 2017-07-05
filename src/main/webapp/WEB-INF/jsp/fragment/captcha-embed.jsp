<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2017 The PWM Project
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

<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.util.CaptchaUtility" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% if (CaptchaUtility.captchaEnabledForRequest(JspUtility.getPwmRequest(pageContext))) { %>
<% CaptchaUtility.prepareCaptchaDisplay(JspUtility.getPwmRequest(pageContext)); %>
<div id="recaptcha-container">
</div>
<noscript>
    <span><pwm:display key="Display_JavascriptRequired"/></span>
    <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
</noscript>
<%-- begin reCaptcha section (http://code.google.com/apis/recaptcha/docs/display.html) --%>
<pwm:script>
    <script type="text/javascript">
        function onloadCallback() {
            var recaptchaCallback = function() {
                console.log('captcha completed, passed');
            };

            console.log('reached google recaptcha onload callback');
            grecaptcha.render('recaptcha-container',{callback:recaptchaCallback,sitekey:'<%=JspUtility.getAttribute(pageContext,PwmRequestAttribute.CaptchaPublicKey)%>'});
        }
    </script>
</pwm:script>
<script nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" src="<%=(String)JspUtility.getAttribute(pageContext,PwmRequestAttribute.CaptchaClientUrl)%>?onload=onloadCallback&render=explicit" defer async></script>
<% } %>