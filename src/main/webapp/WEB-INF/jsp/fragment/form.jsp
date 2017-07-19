<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.config.FormUtility" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="java.util.Collections" %>
<%@ page import="password.pwm.config.CustomLinkConfiguration" %>

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

<%@ taglib uri="pwm" prefix="pwm" %>

    <% final PwmRequest formPwmRequest = PwmRequest.forRequest(request,response); %>
    <% final List<FormConfiguration> formConfigurationList = (List<FormConfiguration>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormConfiguration); %>
    <% final List<CustomLinkConfiguration> linkConfigurationList = (List<CustomLinkConfiguration>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormCustomLinks); %>
    <% final Locale formLocale = formPwmRequest.getLocale(); %>
    <% if (linkConfigurationList != null) { %>
        <% for (final CustomLinkConfiguration loopList : linkConfigurationList) { %>
           <% if (loopList.isCustomLinkNewWindow()) { %>
                <a href=<%=loopList.getcustomLinkUrl()%> title=<%=loopList.getDescription(formLocale)%> target="_blank"><%=loopList.getLabel(formLocale)%></a>
            <% } else { %>
                <a href=<%=loopList.getcustomLinkUrl()%> title=<%=loopList.getDescription(formLocale)%>><%=loopList.getLabel(formLocale)%></a>
            <% } %>
        <% } %>
    <% } %>

    <hr>
<% if (formConfigurationList == null) { %>
[ form definition is not available ]
<% } else if (formConfigurationList.isEmpty()) { %>
<!-- form contains no items ] -->
<% } else { %>
<%
    final boolean forceReadOnly = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormReadOnly);
    final boolean showPasswordFields = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormShowPasswordFields);
    final Map<FormConfiguration,String> formDataMap = (Map<FormConfiguration,String>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormData);
    final Map<String,String> initalValues = (JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormInitialValues) != null)
            ? (Map<String,String>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormInitialValues)
            : Collections.<String,String>emptyMap();

    final PwmApplication pwmApplication = formPwmRequest.getPwmApplication();
    for (final FormConfiguration loopConfiguration : formConfigurationList) {
        String currentValue = formDataMap != null ? formDataMap.get(loopConfiguration) : "";
        currentValue = currentValue == null ? "" : currentValue;
        currentValue = StringUtil.escapeHtml(currentValue);

%>
<div class="formFieldWrapper">
    <% if (loopConfiguration.getType().equals(FormConfiguration.Type.hidden)) { %>
    <input style="text-align: left;" id="<%=loopConfiguration.getName()%>" type="hidden" class="inputfield"
           name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"/>
    <% } else if (loopConfiguration.getType().equals(FormConfiguration.Type.customLink)) { %>
        <% if (loopConfiguration.isCustomLinkNewWindow()) { %>
            <a href=<%=loopConfiguration.getcustomLinkUrl()%> title=<%=loopConfiguration.getDescription(formLocale)%> target="_blank"><%=loopConfiguration.getLabel(formLocale)%></a>
        <% } else { %>
            <a href=<%=loopConfiguration.getcustomLinkUrl()%> title=<%=loopConfiguration.getDescription(formLocale)%>><%=loopConfiguration.getLabel(formLocale)%></a>
        <% } %>
    <% } else if (loopConfiguration.getType().equals(FormConfiguration.Type.checkbox)) { %>
    <% final boolean checked = FormUtility.checkboxValueIsChecked(formDataMap.get(loopConfiguration)); %>
    <label class="checkboxWrapper">
        <input id="<%=loopConfiguration.getName()%>" name="<%=loopConfiguration.getName()%>" type="checkbox" <%=checked?"checked":""%> <pwm:autofocus/>/>
        <%=loopConfiguration.getLabel(formLocale)%>
    </label>
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
    <% if (readonly) { %>
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
    <% } else { %>
    <input style="text-align: left;" id="<%=loopConfiguration.getName()%>" type="<%=loopConfiguration.getType()%>" class="inputfield"
           name="<%=loopConfiguration.getName()%>" value="<%= currentValue %>"
        <%if (loopConfiguration.getRegex() != null) {%> pattern="<%=loopConfiguration.getRegex()%>"<%}%>
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
            <%if (loopConfiguration.getRegex() != null) {%> pattern="<%=loopConfiguration.getRegex()%>"<%}%>
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
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHOW_STRENGTH_METER)) { %>
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
            <% } %>
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
