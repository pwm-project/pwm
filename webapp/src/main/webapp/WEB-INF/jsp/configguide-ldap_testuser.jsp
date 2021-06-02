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


<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm" name="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div id="outline_ldap" class="setting_outline">
                <div class="setting_title">
                    LDAP Test User (Optional)
                </div>
                <div class="setting_body">
                    <pwm:display key="ldap_testuser_description" bundle="ConfigGuide"/>
                    <div class="setting_item">
                        <label class="checkboxWrapper"><input type="radio" id="<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>-enabled" name="<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>" value="true">Enabled</label>
                        <br/>
                        <label class="checkboxWrapper"><input type="radio" id="<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>-disabled" name="<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>" value="false">Disabled</label>
                        <br/>
                        <br/>
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>" style="padding-left: 5px; padding-top: 5px" style="display: none">
                            Example: <code><%=PwmSetting.LDAP_TEST_USER_DN.getExample(ConfigGuideForm.generateStoredConfig(configGuideBean).getTemplateSet())%></code>
                            <br/><br/>
                            <b>LDAP Test User DN</b>
                            <br/>
                            <input style="width:400px" class="configStringInput" id="<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>" name="<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_TEST_USER))%>" autofocus/>
                            <button type="button" class="btn" id="button-browse-testUser">
                                <span class="btn-icon pwm-icon pwm-icon-sitemap"></span>
                                <pwm:display key="Button_Browse"/>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="healthBody" class="noborder nomargin nopadding" style="cursor: pointer">
            <div style="text-align: center">
                <button class="menubutton" style="margin-left: auto; margin-right: auto">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckSettings" bundle="Config"/>
                </button>
            </div>
        </div>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    function handleFormActivity() {
        PWM_GUIDE.updateForm();
        clearHealthDiv();
        checkIfNextEnabled();

        if (PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>-enabled').checked) {
            PWM_MAIN.getObject('titlePane_<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>').style.display = 'inline';
        } else {
            PWM_MAIN.getObject('titlePane_<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>').style.display = 'none';
        }
    }

    function clearHealthDiv() {
        PWM_MAIN.getObject('healthBody').innerHTML = PWM_VAR['originalHealthBody'];
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_VAR['originalHealthBody'] = PWM_MAIN.getObject('healthBody').innerHTML;
        checkIfNextEnabled();
        initEnabledField();

        PWM_MAIN.addEventHandler('configForm','input,click',function(){
            handleFormActivity();
        });


        PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
        PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

        PWM_MAIN.addEventHandler('healthBody','click',function(){loadHealth()});

        PWM_MAIN.addEventHandler('button-browse-testUser','click',function(){
            UILibrary.editLdapDN(function(value){
                PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>').value = value;
                handleFormActivity();
            })
        });
    });

    function checkIfNextEnabled() {
        var fieldValue = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER%>').value;
        if (PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>-enabled').checked) {
            var goodHealth = false;
            var hasValue = true;

            if (PWM_GLOBAL['pwm-health'] !== 'GOOD' && PWM_GLOBAL['pwm-health'] !== 'CONFIG') {
                goodHealth = true;
            }

            if (fieldValue.length > 0) {
                hasValue = true;
            }

            PWM_MAIN.getObject('button_next').disabled = (goodHealth && hasValue)
        } else {
            PWM_MAIN.getObject('button_next').disabled = false;
        }
    }

    function initEnabledField() {
        <% final String currentValue = configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED);%>
        <% if ("true".equals(currentValue)) { %>
        PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>-enabled').checked = true;
        <% } else if ("false".equals(currentValue)) { %>
        PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED%>-disabled').checked = true;
        <% } %>

        handleFormActivity();
    }

    function loadHealth() {
        var options = {};
        options['sourceUrl'] = 'ConfigGuide?processAction=ldapHealth';
        options['showRefresh'] = false;
        options['refreshTime'] = -1;
        options['finishFunction'] = function(){
            PWM_MAIN.closeWaitDialog();
            checkIfNextEnabled();
        };
        PWM_MAIN.showWaitDialog({loadFunction:function(){
            PWM_ADMIN.showAppHealth('healthBody', options);
        }});
    }
</script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
