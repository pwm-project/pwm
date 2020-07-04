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


<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.value.data.FormConfiguration" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.servlet.updateprofile.UpdateProfileServlet" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.form.FormUtility" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest formPwmRequest = PwmRequest.forRequest(request,response); %>
<% final Locale formLocale = formPwmRequest.getLocale(); %>
<% final List<FormConfiguration> formConfigurationList = (List<FormConfiguration>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormConfiguration); %>
<% if (JavaHelper.isEmpty(formConfigurationList)) { %>
<!-- [ form definition is not available ] -->
<% } else { %>
<%
    final boolean forceReadOnly = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormReadOnly);
    final boolean showPasswordFields = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormShowPasswordFields);
    final Map<FormConfiguration,String> formDataMap = (Map<FormConfiguration,String>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormData);

    final PwmApplication pwmApplication = formPwmRequest.getPwmApplication();
    for (final FormConfiguration loopConfiguration : formConfigurationList) {
        String currentValue = formDataMap != null ? formDataMap.get(loopConfiguration) : "";
        currentValue = currentValue == null ? "" : currentValue;
        currentValue = StringUtil.escapeHtml(currentValue);

%>
<div class="formFieldWrapper" id="formFieldWrapper-<%=loopConfiguration.getName()%>">
    <% if (loopConfiguration.getType().equals(FormConfiguration.Type.hidden)) { %>
    <input id="<%=loopConfiguration.getName()%>" type="hidden" class="inputfield"
           name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"/>
    <% } else if (loopConfiguration.getType().equals(FormConfiguration.Type.checkbox)) { %>
    <% final boolean checked = FormUtility.checkboxValueIsChecked(formDataMap.get(loopConfiguration)); %>
    <label class="checkboxWrapper">
        <input id="<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" type="checkbox" <%=checked?"checked":""%> <pwm:autofocus/>/>
        <%=loopConfiguration.getLabel(formLocale)%>
        <%if(loopConfiguration.isRequired()){%>
        <span style="font-style: italic; font-size: smaller" id="label_required_<%=loopConfiguration.getName()%>">*&nbsp;</span>
        <%}%>
    </label>
    <% if (loopConfiguration.getDescription(formLocale) != null && loopConfiguration.getDescription(formLocale).length() > 0) { %>
    <p><%=loopConfiguration.getDescription(formLocale)%></p>
    <% } %>
    <% } else { %>
    <label for="<%=loopConfiguration.getName()%>">
        <div class="formFieldLabel">
            <%= loopConfiguration.getLabel(formLocale) %>
            <%if(loopConfiguration.isRequired()){%>
            <span style="font-style: italic; font-size: smaller" id="label_required_<%=loopConfiguration.getName()%>">*&nbsp;</span>
            <%}%>
        </div>
    </label>
    <% if (loopConfiguration.getDescription(formLocale) != null && loopConfiguration.getDescription(formLocale).length() > 0) { %>
    <p><%=loopConfiguration.getDescription(formLocale)%></p>
    <% } %>
    <% final boolean readonly = loopConfiguration.isReadonly() || forceReadOnly; %>
    <% if (readonly && loopConfiguration.getType() != FormConfiguration.Type.photo) { %>
    <span id="<%=loopConfiguration.getName()%>">
        <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
        <%= currentValue %>
        </span>
    <% } else if (loopConfiguration.getType() == FormConfiguration.Type.select) { %>
    <select id="<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" class="inputfield selectfield" <pwm:autofocus/> >
        <% for (final String optionName : loopConfiguration.getSelectOptions().keySet()) {%>
        <option value="<%=optionName%>" <%if(optionName.equals(currentValue)){%>selected="selected"<%}%>>
            <%=loopConfiguration.getSelectOptions().get(optionName)%>
        </option>
        <% } %>
    </select>
    <% } else if (loopConfiguration.getType() == FormConfiguration.Type.photo ) { %>
    <div class="formfield-photo-wrapper">
        <% if (StringUtil.isEmpty( currentValue) ) { %>
        <div id="<%=loopConfiguration.getName()%>" class="formfield-photo-missing">
        </div>
        <% } else { %>
        <div class="formfield-photo-image-wrapper">
            <img class="formfield-photo" src="<pwm:current-url/>?processAction=readPhoto&field=<%=loopConfiguration.getName()%>"/>
        </div>
        <% } %>
        <% if (!readonly) { %>
        <div class="formfield-photo-controls-wrapper">
            <button type="button" id="button-uploadPhoto-<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" class="btn" title="<pwm:display key="Button_Upload"/>">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                <pwm:display key="Button_Upload"/>
            </button>
            <pwm:script>
                <script type="application/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        PWM_MAIN.addEventHandler('button-uploadPhoto-<%=loopConfiguration.getName()%>',"click",function(){
                            var accept = '<%=StringUtil.collectionToString(loopConfiguration.getMimeTypes())%>';
                            PWM_UPDATE.uploadPhoto('<%=loopConfiguration.getName()%>',{accept:accept});
                        });
                    });
                </script>
            </pwm:script>
            <% if (!StringUtil.isEmpty( currentValue) ) { %>
            <button type="button" id="button-deletePhoto-<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" class="btn" title="<pwm:display key="Button_Delete"/>">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                <pwm:display key="Button_Delete"/>
            </button>
            <pwm:script>
                <script type="application/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        PWM_MAIN.addEventHandler('button-deletePhoto-<%=loopConfiguration.getName()%>',"click",function(){
                            PWM_MAIN.showConfirmDialog({okAction:function(){
                                    PWM_MAIN.submitPostAction(window.location.pathname, 'deletePhoto', {field:'<%=loopConfiguration.getName()%>'});
                                }
                            })
                        });
                    });
                </script>
            </pwm:script>
            <% } %>
        </div>
        <% } %>
    </div>
    <% } else { %>
    <input id="<%=loopConfiguration.getName()%>" type="<%=loopConfiguration.getType()%>" class="inputfield"
           name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"
    <pwm:if test="<%=PwmIfTest.clientFormShowRegexEnabled%>">
            <%if (!StringUtil.isEmpty(loopConfiguration.getRegex())) {%> pattern="<%=loopConfiguration.getRegex()%>"<%}%>
    </pwm:if>
        <%if(loopConfiguration.getPlaceholder()!=null){%> placeholder="<%=loopConfiguration.getPlaceholder()%>"<%}%>
        <%if(loopConfiguration.isRequired()){%> required="required"<%}%>
    <pwm:autofocus/> maxlength="<%=loopConfiguration.getMaximumLength()%>">
    <% if (loopConfiguration.isConfirmationRequired() && !forceReadOnly && !loopConfiguration.isReadonly() && loopConfiguration.getType() != FormConfiguration.Type.hidden && loopConfiguration.getType() != FormConfiguration.Type.select) { %>
    <label for="<%=loopConfiguration.getName()%>_confirm">
        <div class="formFieldLabel">
            <pwm:display key="Field_Confirm_Prefix"/>&nbsp;<%=loopConfiguration.getLabel(formLocale) %>
            <%if(loopConfiguration.isRequired()){%>*<%}%>
        </div>
    </label>
    <input style="" id="<%=loopConfiguration.getName()%>_confirm" type="<%=loopConfiguration.getType()%>" class="inputfield"
           name="<%=loopConfiguration.getName()%>_confirm"
            <pwm:if test="<%=PwmIfTest.clientFormShowRegexEnabled%>">
                <%if (!StringUtil.isEmpty(loopConfiguration.getRegex())) {%> pattern="<%=loopConfiguration.getRegex()%>"<%}%>
            </pwm:if>
            <%if(loopConfiguration.isRequired()){%> required="required"<%}%>
            <%if(loopConfiguration.isReadonly()){%> readonly="readonly"<%}%>
           maxlength="<%=loopConfiguration.getMaximumLength()%>"/>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.addEventHandler('<%=loopConfiguration.getName()%>','keypress',function(){
                    PWM_MAIN.getObject('<%=loopConfiguration.getName()%>_confirm').value='';
                });
            });
        </script>
    </pwm:script>
    <% } %>
    <% } %>
    <% } %>
    <% if (loopConfiguration.getJavascript() != null && loopConfiguration.getJavascript().length() > 0) { %>
    <pwm:script>
        <script type="text/javascript">
            try {
                <%=loopConfiguration.getJavascript()%>
            } catch (e) {
                console.log('error executing custom javascript for form field \'' + <%=loopConfiguration.getName()%> + '\', error: ' + e)
            }
        </script>
    </pwm:script>
    <% } %>
    <pwm:if test="<%=PwmIfTest.clientFormShowRegexEnabled%>">
        <% if (loopConfiguration.getRegexError(formLocale) != null && loopConfiguration.getRegexError(formLocale).length() > 0) { %>
        <pwm:script>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('<%=loopConfiguration.getName()%>', 'input', function (event) {
                        var input = event.target;
                        var regexError = '<%=StringUtil.escapeJS(loopConfiguration.getRegexError(formLocale))%>';
                        var msg = input.value.search(new RegExp(input.getAttribute('pattern'))) >= 0 ? '' : regexError;
                        input.setCustomValidity(msg);
                        input.title = msg;
                    });
                });
            </script>
        </pwm:script>
        <% } %>
    </pwm:if>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.showTooltip({
                    id: "label_required_<%=loopConfiguration.getName()%>",
                    text: '<%=PwmError.ERROR_FIELD_REQUIRED.getLocalizedMessage(formLocale,pwmApplication.getConfig(),new String[]{loopConfiguration.getLabel(formLocale)})%>',
                    position: ['above']
                });
            });
        </script>
    </pwm:script>
</div>
<% } %>

<% if (showPasswordFields) { %>
<h2>
    <label for="password1"><pwm:display key="Field_NewPassword"/>
        <span style="font-style: italic;font-size:smaller" id="label_required_password">*&nbsp;</span>
        <pwm:script>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.showTooltip({
                        id: "label_required_password",
                        text: '<%=PwmError.ERROR_FIELD_REQUIRED.getLocalizedMessage(formLocale,pwmApplication.getConfig(),new String[]{JspUtility.getMessage(pageContext,Display.Field_NewPassword)})%>',
                        position: ['above']
                    });
                });
            </script>
        </pwm:script>
    </label>
</h2>
<div id="PasswordRequirements">
    <ul>
        <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>" form="newuser"/>
    </ul>
</div>
<table class="noborder nomargin nopadding">
    <tr class="noborder nomargin nopadding">
        <td class="noborder nomargin nopadding" style="width:60%">
            <input type="<pwm:value name="passwordFieldType"/>" name="password1" id="password1" class="changepasswordfield passwordfield" style="margin-left:5px"/>
        </td>
        <td class="noborder">
            <pwm:if test="<%=PwmIfTest.showStrengthMeter%>">
            <div id="strengthBox" style="visibility:hidden;">
                <div id="strengthLabel">
                    <pwm:display key="Display_StrengthMeter"/>
                </div>
                <div class="progress-container">
                    <div id="strengthBar" style="width: 0">&nbsp;</div>
                </div>
            </div>
            <pwm:script>
                <script type="text/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        PWM_MAIN.showTooltip({
                            id: ["strengthBox"],
                            text: PWM_MAIN.showString('Tooltip_PasswordStrength'),
                            width: 350
                        });
                    });
                </script>
            </pwm:script>
            </pwm:if>
        </td>
        <td class="noborder" style="width:10%">&nbsp;</td>
    </tr>
    <tr class="noborder nomargin nopadding">
        <td class="noborder nomargin nopadding">
            <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" name="password2" id="password2" class="changepasswordfield passwordfield" style="margin-left:5px"/>
        </td>
        <td class="noborder">
            <%-- confirmation mark [not shown initially, enabled by javascript; see also changepassword.js:markConfirmationMark() --%>
            <div style="padding-top:10px;">
                <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                     src="<pwm:context/><pwm:url url='/public/resources/greenCheck.png'/>">
                <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                     src="<pwm:context/><pwm:url url='/public/resources/redX.png'/>">
            </div>
        </td>
        <td class="noborder" style="width:10%">&nbsp;</td>
    </tr>
</table>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('password1','keypress',function(){
                PWM_MAIN.getObject('password2').value='';
            });
        });
    </script>
</pwm:script>
<% } %>
<% } %>
