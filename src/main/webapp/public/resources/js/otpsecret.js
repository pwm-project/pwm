/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

var PWM_OTP = PWM_OTP || {};

PWM_OTP.checkExistingCode = function() {
    PWM_MAIN.getObject('button-verifyCode').disabled = true;
    PWM_MAIN.getObject('crossIcon').style.display = 'none';
    PWM_MAIN.getObject('checkIcon').style.display = 'none';
    PWM_MAIN.getObject('workingIcon').style.display = 'inherit';
    PWM_MAIN.pwmFormValidator({
        serviceURL:"SetupOtp?processAction=restValidateCode",
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
        + '<span style="display:none;color:green" id="checkIcon" class="btn-icon fa fa-lg fa-check"></span>'
        + '<span style="display:none;color:red" id="crossIcon" class="btn-icon fa fa-lg fa-times"></span>'
        + '<span style="display:none" id="workingIcon" class="fa fa-lg fa-spin fa-spinner"></span>'
        + '</td><td style="width:150px">'
        + '<button type="submit" name="button-verifyCode" class="btn" id="button-verifyCode">'
        + '<span class="btn-icon fa fa-check"></span>' + PWM_MAIN.showString("Button_CheckCode") + '</button>'
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
