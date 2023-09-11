<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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


<%@ page import="password.pwm.PwmDomain" %>
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
<%@ page import="password.pwm.util.java.CollectionUtil" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest formPwmRequest = PwmRequest.forRequest(request,response); %>
<% final Locale formLocale = formPwmRequest.getLocale(); %>
<% final List<FormConfiguration> formConfigurationList = (List<FormConfiguration>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormConfiguration); %>
<% if ( CollectionUtil.isEmpty(formConfigurationList)) { %>
<!-- [ form definition is not available ] -->
<% } else { %>
<%
    final boolean forceReadOnly = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormReadOnly);
    final boolean showPasswordFields = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormShowPasswordFields);
    final Map<FormConfiguration,String> formDataMap = (Map<FormConfiguration,String>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormData);

    final PwmDomain pwmDomain = formPwmRequest.getPwmDomain();
    for (final FormConfiguration loopConfiguration : formConfigurationList) {
        String currentValue = formDataMap != null ? formDataMap.get(loopConfiguration) : "";
        currentValue = currentValue == null ? "" : currentValue;
        currentValue = StringUtil.escapeHtml(currentValue);
        final String requiredLabel = PwmError.ERROR_FIELD_REQUIRED.getLocalizedMessage(formLocale,pwmDomain.getConfig(),new String[]{loopConfiguration.getLabel(formLocale)});

%>
<div class="formFieldWrapper" id="formFieldWrapper-<%=loopConfiguration.getName()%>">
    <% if (loopConfiguration.getType().equals(FormConfiguration.Type.hidden)) { %>
    <input id="<%=loopConfiguration.getName()%>" type="hidden" class="inputfield"
           name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"/> tabindex="<pwm:tabindex/>"
    <% } else if (loopConfiguration.getType().equals(FormConfiguration.Type.checkbox)) { %>
    <% final boolean checked = FormUtility.checkboxValueIsChecked(formDataMap.get(loopConfiguration)); %>
    <label class="checkboxWrapper">
        <input id="<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>"
               type="checkbox" <%=checked?"checked":""%> <pwm:autofocus/>/>
        <%=loopConfiguration.getLabel(formLocale)%>
        <%if(loopConfiguration.isRequired()){%>
        <span class="formFieldRequiredAsterisk" id="label_required_<%=loopConfiguration.getName()%>"
              title="<%=requiredLabel%>3">*</span>
        <%}%>
    </label>
    <% if (loopConfiguration.getDescription(formLocale) != null && loopConfiguration.getDescription(formLocale).length() > 0) { %>
    <div class="formFieldDescription"><%=loopConfiguration.getDescription(formLocale)%></div>
    <% } %>
    <% } else { %>
    <div class="formFieldLabel">
        <label for="<%=loopConfiguration.getName()%>">
            <%= loopConfiguration.getLabel(formLocale) %>
            <%if(loopConfiguration.isRequired()){%>
            <span class="formFieldRequiredAsterisk" id="label_required_<%=loopConfiguration.getName()%>"
                  title="<%=requiredLabel%>2">*</span>
            <%}%>
        </label>
    </div>
    <% if (loopConfiguration.getDescription(formLocale) != null && loopConfiguration.getDescription(formLocale).length() > 0) { %>
    <div class="formFieldDescription"><%=loopConfiguration.getDescription(formLocale)%></div>
    <% } %>
    <% final boolean readonly = loopConfiguration.isReadonly() || forceReadOnly; %>
    <% if (readonly && loopConfiguration.getType() != FormConfiguration.Type.photo) { %>
    <span id="<%=loopConfiguration.getName()%>">
        <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
        <%= currentValue %>
        </span>
    <% } else if (loopConfiguration.getType() == FormConfiguration.Type.select) { %>
    <select id="<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" class="inputfield selectfield"
            <pwm:autofocus/> tabindex="<pwm:tabindex/>">
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
            <script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
                import {PWM_MAIN} from "<pwm:url url="/public/resources/js/main.js" addContext="true"/>";
                import {PWM_UPDATE} from "<pwm:url url="/public/resources/js/updateprofile.js" addContext="true"/>";
                PWM_MAIN.addEventHandler('button-uploadPhoto-<%=loopConfiguration.getName()%>',"click",function(){
                    const accept = '<%=StringUtil.collectionToString(formPwmRequest.getAppConfig().permittedPhotoMimeTypes())%>';
                    PWM_UPDATE.uploadPhoto('<%=loopConfiguration.getName()%>',{accept:accept});
                });
            </script>
            <% if (!StringUtil.isEmpty( currentValue) ) { %>
            <button type="button" id="button-deletePhoto-<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" class="btn" title="<pwm:display key="Button_Delete"/>">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                <pwm:display key="Button_Delete"/>
            </button>
            <script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
                import {PWM_MAIN} from "<pwm:url url="/public/resources/js/main.js" addContext="true"/>";
                PWM_MAIN.addEventHandler('button-deletePhoto-<%=loopConfiguration.getName()%>',"click",function(){
                    PWM_MAIN.showConfirmDialog({okAction:function(){
                            PWM_MAIN.submitPostAction(window.location.pathname, 'deletePhoto', {field:'<%=loopConfiguration.getName()%>'});
                        }
                    })
                });
            </script>
            <% } %>
        </div>
        <% } %>
    </div>
    <% } else { %>
    <input id="<%=loopConfiguration.getName()%>" type="<%=loopConfiguration.getType()%>" class="inputfield"
           name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>" tabindex="<pwm:tabindex/>"
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
            <%if(loopConfiguration.isRequired()){%>
            <span class="formFieldRequiredAsterisk" id="label_required_<%=loopConfiguration.getName()%>"
                  title="<%=requiredLabel%>2">*</span>
            <%}%>
        </div>
    </label>
    <input id="<%=loopConfiguration.getName()%>_confirm" type="<%=loopConfiguration.getType()%>" class="inputfield"
           name="<%=loopConfiguration.getName()%>_confirm" tabindex="<pwm:tabindex/>"
            <pwm:if test="<%=PwmIfTest.clientFormShowRegexEnabled%>">
                <%if (!StringUtil.isEmpty(loopConfiguration.getRegex())) {%> pattern="<%=loopConfiguration.getRegex()%>"<%}%>
            </pwm:if>
            <%if(loopConfiguration.isRequired()){%> required="required"<%}%>
            <%if(loopConfiguration.isReadonly()){%> readonly="readonly"<%}%>
           maxlength="<%=loopConfiguration.getMaximumLength()%>"/>

    <script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
        import {PWM_MAIN} from "<pwm:url url="/public/resources/js/main.js" addContext="true"/>";
        import {PWM_JSLibrary} from "<pwm:url url="/public/resources/js/jslibrary.js" addContext="true"/>";
        PWM_MAIN.addEventHandler('<%=loopConfiguration.getName()%>','keypress',function(){
            PWM_JSLibrary.getElement('<%=loopConfiguration.getName()%>_confirm').value='';
        });
    </script>
    <% } %>
    <% } %>
    <% } %>
    <pwm:if test="<%=PwmIfTest.clientFormShowRegexEnabled%>">
        <% if (loopConfiguration.getRegexError(formLocale) != null && loopConfiguration.getRegexError(formLocale).length() > 0) { %>
        <script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
            import {PWM_MAIN} from "<pwm:url url="/public/resources/js/main.js" addContext="true"/>";
            PWM_MAIN.addEventHandler('<%=loopConfiguration.getName()%>', 'input', function (event) {
                const input = event.target;
                const regexError = '<%=StringUtil.escapeJS(loopConfiguration.getRegexError(formLocale))%>';
                const msg = input.value.search(new RegExp(input.getAttribute('pattern'))) >= 0 ? '' : regexError;
                input.setCustomValidity(msg);
                input.title = msg;
            });
        </script>
        <% } %>
    </pwm:if>
</div>
<% } %>
<% if (showPasswordFields) { %>
<div id="PasswordRequirements">
    <ul>
        <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>"/>
    </ul>
</div>
<div id="PasswordChangeMessage">
    <p><pwm:PasswordChangeMessageTag/></p>
</div>
<jsp:include page="form-field-newpassword.jsp" />
<% } %>
<% } %>
