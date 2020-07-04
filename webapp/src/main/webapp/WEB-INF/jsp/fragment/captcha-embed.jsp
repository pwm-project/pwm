<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
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
            PWM_MAIN.doQuery('.pwm-btn-submit',function(submitButton) {
                submitButton.disabled = true;
            });
            var recaptchaCallback = function() {
                console.log('captcha completed, passed');
                PWM_MAIN.doQuery('.pwm-btn-submit',function(submitButton) {
                    submitButton.disabled = false;
                });
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
<input type="hidden" name="<%=CaptchaUtility.PARAM_RECAPTCHA_FORM_NAME%>" id="<%=CaptchaUtility.PARAM_RECAPTCHA_FORM_NAME%>"/>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            PWM_MAIN.doQuery('.pwm-form-captcha',function(formElement) {
                PWM_MAIN.addEventHandler(formElement, "submit", function(event){
                    PWM_MAIN.log('entering handleCaptchaFormSubmit');
                    PWM_VAR['captcha-form-element'] = formElement;
                    PWM_MAIN.cancelEvent(event);

                    PWM_MAIN.showWaitDialog({loadFunction: function () {
                            try {
                                grecaptcha.execute();
                            } catch (e) {
                                PWM_MAIN.log('error executing invisible recaptcha: ' + e);
                                PWM_FORM.handleFormSubmit(formElement);
                            }
                    }});
                });
            });
        });

        var onloadCaptcha = function() {
            PWM_MAIN.log('entering onloadCaptcha');
        };

        var postCaptchaFormSubmit = function(response) {
            PWM_MAIN.log('entering postCaptchaFormSubmit, response=' + response);
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
