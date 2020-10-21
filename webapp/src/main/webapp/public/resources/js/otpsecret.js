/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var PWM_OTP = PWM_OTP || {};

PWM_OTP.checkExistingCode = function() {
    PWM_MAIN.getObject('button-verifyCode').disabled = true;
    PWM_MAIN.getObject('crossIcon').style.display = 'none';
    PWM_MAIN.getObject('checkIcon').style.display = 'none';
    PWM_MAIN.getObject('workingIcon').style.display = 'inherit';
    PWM_MAIN.pwmFormValidator({
        serviceURL: PWM_MAIN.addParamToUrl(window.location.href,"processAction","restValidateCode"),
        readDataFunction:function(){
            var paramData = { };
            paramData['code'] = PWM_MAIN.getObject('verifyCodeInput').value;
            return paramData;
        },
        showMessage:false,
        processResultsFunction:function(result){
            if (result['data']) {
                PWM_MAIN.getObject('checkIcon').style.display = 'inherit';
                PWM_MAIN.getObject('crossIcon').style.display = 'none';
            } else {
                PWM_MAIN.getObject('checkIcon').style.display = 'none';
                PWM_MAIN.getObject('crossIcon').style.display = 'inherit';
            }
            PWM_MAIN.getObject('verifyCodeInput').value = '';
            PWM_MAIN.getObject('verifyCodeInput').focus();
        },
        completeFunction:function(){
            PWM_MAIN.getObject('button-verifyCode').disabled = false;
            PWM_MAIN.getObject('workingIcon').style.display = 'none';
        }
    });
};

PWM_OTP.openCheckCodeDialog = function() {
    var templateHtml = '<div>'
        + '<p>' + PWM_MAIN.showString("Display_RecoverOTP") + '</p>'
        + '<table class="noborder" style="width: 300px; table-layout: fixed">'
        + '<tr><td style="width:115px">'
        + '<input type="text" class="inputfield" style="max-width: 100px; width: 100px" pattern="[0-9].*" id="verifyCodeInput" autofocus maxlength="6" />'
        + '</td><td style="width:20px">'
        + '<span style="display:none;color:green" id="checkIcon" class="btn-icon pwm-icon pwm-icon-lg pwm-icon-check"></span>'
        + '<span style="display:none;color:red" id="crossIcon" class="btn-icon pwm-icon pwm-icon-lg pwm-icon-times"></span>'
        + '<span style="display:none" id="workingIcon" class="pwm-icon pwm-icon-lg pwm-icon-spin pwm-icon-spinner"></span>'
        + '</td><td style="width:150px">'
        + '<button type="submit" name="button-verifyCode" class="btn" id="button-verifyCode">'
        + '<span class="btn-icon pwm-icon pwm-icon-check"></span>' + PWM_MAIN.showString("Button_CheckCode") + '</button>'
        + '</td></tr></table></div>';

    PWM_MAIN.showDialog({
        title:PWM_MAIN.showString('Button_CheckCode'),
        text:templateHtml,
        showClose:true,
        loadFunction: function(){
            PWM_MAIN.addEventHandler('button-verifyCode','click',function(){
                PWM_OTP.checkExistingCode();
            });
        }
    });
};

PWM_OTP.initExistingOtpPage = function() {
    PWM_MAIN.addEventHandler('button-verifyCodeDialog','click',function(){
        PWM_OTP.openCheckCodeDialog();
    });
    PWM_MAIN.getObject('continue_button').type = 'button';
    PWM_MAIN.addEventHandler('continue_button','click',function(){
        PWM_MAIN.showConfirmDialog({
            text: PWM_MAIN.showString("Display_OtpClearWarning"),
            okAction:function(){
                PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('setupOtpSecretForm'));
            }
        });
    });
};
