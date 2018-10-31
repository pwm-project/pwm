<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.util.CaptchaUtility" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final CaptchaUtility.CaptchaMode captchaMode = CaptchaUtility.readCaptchaMode( JspUtility.getPwmRequest( pageContext ) ); %>
<% final boolean captchaEnabled = CaptchaUtility.captchaEnabledForRequest( JspUtility.getPwmRequest(pageContext) ); %>
<% if (captchaEnabled ) { %>
<% CaptchaUtility.prepareCaptchaDisplay(JspUtility.getPwmRequest(pageContext)); %>
<div id="recaptcha-container">
</div>
<noscript>
    <span><pwm:display key="Display_JavascriptRequired"/></span>
    <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
</noscript>
<% if ( captchaMode == CaptchaUtility.CaptchaMode.V3 ) { %>
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
<% if ( captchaMode == CaptchaUtility.CaptchaMode.V3_INVISIBLE ) { %>
<!-- captcha v3-invisible 1.0 -->
<input type="hidden" name="g-recaptcha-response" id="g-recaptcha-response"/>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            PWM_MAIN.doQuery('.pwm-form-captcha',function(formElement) {
                PWM_MAIN.addEventHandler(formElement, "submit", function(event){
                    console.log('entering handleCaptchaFormSubmit');

                    PWM_VAR['captcha-form-element'] = formElement;
                    PWM_MAIN.cancelEvent(event);

                    grecaptcha.execute();
                });
            });


        });

        var onloadCaptcha = function() {
            console.log('entering onloadCaptcha');
        };

        var postCaptchaFormSubmit = function(response) {
            console.log('entering postCaptchaFormSubmit, response=' + response);
            var form = PWM_VAR['captcha-form-element'];
            PWM_MAIN.getObject('g-recaptcha-response').value = response;
            PWM_MAIN.handleFormSubmit(form);
        };
    </script>
</pwm:script>
<div class="g-recaptcha"
     data-sitekey="<%=JspUtility.getAttribute(pageContext,PwmRequestAttribute.CaptchaPublicKey)%>"
     data-callback="postCaptchaFormSubmit"
     data-size="invisible">
</div>
<script nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" src="<%=(String)JspUtility.getAttribute(pageContext,PwmRequestAttribute.CaptchaClientUrl)%>?onload=onloadCaptcha" async defer></script>
<% } %>
<% } %>
