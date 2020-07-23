<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2019 The PWM Project
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


<%@ page import="password.pwm.config.value.FileValue" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% final Locale userLocale = JspUtility.locale(request); %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <%=PwmSetting.DATABASE_JDBC_DRIVER.getLabel(userLocale)%>
                </div>
                <div class="setting_body">
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_CLASSNAME%>" style="padding-left: 5px; padding-top: 5px">
                            <%=PwmSetting.DATABASE_JDBC_DRIVER.getDescription(userLocale)%>
                            <br/>
                            <% if (configGuideBean.getDatabaseDriver() != null && !configGuideBean.getDatabaseDriver().toInfoMap().isEmpty()) { %>
                            <% final FileValue.FileInfo fileInfo = configGuideBean.getDatabaseDriver().toInfoMap().iterator().next(); %>
                            <table style="max-width: 400px">
                                <tr>
                                    <td class="key">Name</td><td><%=fileInfo.getName()%></td>
                                </tr>
                                <tr>
                                    <td class="key">Type</td><td><%=fileInfo.getType()%></td>
                                </tr>
                                <tr>
                                    <td class="key">Size</td><td><%=fileInfo.getSize()%></td>
                                </tr>
                                <tr>
                                    <td class="key">sha512</td><td><%=fileInfo.getSha512sum()%></td>
                                </tr>
                            </table>
                            <% }  %>
                            <button type="button" id="button-uploadJDBCDriver" class="btn">
                                <span class="btn-icon pwm-icon pwm-icon-upload"></span>
                                Upload JDBC JAR Driver
                            </button>

                        </div>
                    </div>
                </div>
            </div>


            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <%=PwmSetting.DATABASE_CLASS.getLabel(userLocale)%>
                </div>
                <div class="setting_body">
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_CLASSNAME%>" style="padding-left: 5px; padding-top: 5px">
                            <label>
                                <%=PwmSetting.DATABASE_CLASS.getDescription(userLocale)%>
                                <br/>
                                <input class="configStringInput" id="<%=ConfigGuideFormField.PARAM_DB_CLASSNAME%>" name="<%=ConfigGuideFormField.PARAM_DB_CLASSNAME%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_DB_CLASSNAME))%>" required autofocus/>
                            </label>
                        </div>
                    </div>
                </div>
            </div>

            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <%=PwmSetting.DATABASE_URL.getLabel(userLocale)%>
                </div>
                <div class="setting_body">
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_CONNECT_URL%>" style="padding-left: 5px; padding-top: 5px">
                            <label>
                                <%=PwmSetting.DATABASE_URL.getDescription(userLocale)%>
                                <br/>
                                <input class="configStringInput" id="<%=ConfigGuideFormField.PARAM_DB_CONNECT_URL%>" name="<%=ConfigGuideFormField.PARAM_DB_CONNECT_URL%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_DB_CONNECT_URL))%>" required autofocus/>
                            </label>
                        </div>
                    </div>
                </div>
            </div>


            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <%=PwmSetting.DATABASE_USERNAME.getLabel(userLocale)%>
                </div>
                <div class="setting_body">
                <div class="setting_item">
                    <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_USERNAME%>" style="padding-left: 5px; padding-top: 5px">
                        <label>
                            <%=PwmSetting.DATABASE_USERNAME.getDescription(userLocale)%>
                            <br/>
                            <input class="configStringInput" id="<%=ConfigGuideFormField.PARAM_DB_USERNAME%>" name="<%=ConfigGuideFormField.PARAM_DB_USERNAME%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_DB_USERNAME))%>" required autofocus/>
                        </label>
                    </div>
                </div>
                </div>
            </div>

            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <%=PwmSetting.DATABASE_PASSWORD.getLabel(userLocale)%>
                </div>

                <div class="setting_body">
                <div class="setting_item">
                    <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_PASSWORD%>" style="padding-left: 5px; padding-top: 5px">
                        <label>
                            <%=PwmSetting.DATABASE_PASSWORD.getDescription(userLocale)%>
                            <br/>
                            <input style="width:200px" type="password" class="configStringInput passwordfield" id="<%=ConfigGuideFormField.PARAM_DB_PASSWORD%>" name="<%=ConfigGuideFormField.PARAM_DB_PASSWORD%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_DB_PASSWORD))%>" required autofocus/>
                        </label>
                    </div>
                </div>
                </div>
            </div>

            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <%=PwmSetting.DB_VENDOR_TEMPLATE.getLabel(userLocale)%>
                </div>

                <div class="setting_body">
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_VENDOR%>" style="padding-left: 5px; padding-top: 5px">
                            <label>
                                <%=PwmSetting.DB_VENDOR_TEMPLATE.getDescription(userLocale)%>
                                <br/>

                                <% final String selectedTemplate = configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_DB_VENDOR); %>

                                <select id="<%=ConfigGuideFormField.PARAM_DB_VENDOR%>" name="<%=ConfigGuideFormField.PARAM_DB_VENDOR%>">
                                    <% if (selectedTemplate == null || selectedTemplate.isEmpty()) { %>
                                    <option value="NOTSELECTED" selected disabled> -- Please select a template -- </option>
                                    <% } %>
                                    <% for (final String loopTemplate : PwmSetting.DB_VENDOR_TEMPLATE.getOptions().keySet()) { %>
                                    <% final boolean selected = loopTemplate.equals(selectedTemplate); %>
                                    <option value="<%=loopTemplate%>"<% if (selected) { %> selected="selected"<% } %>>
                                        <%=PwmSetting.DB_VENDOR_TEMPLATE.getOptions().get(loopTemplate)%>
                                    </option>
                                    <% } %>
                                </select>
                            </label>
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
        }

        function clearHealthDiv() {
            PWM_MAIN.getObject('healthBody').innerHTML = PWM_VAR['originalHealthBody'];
        }

        function uploadJDBCDriver() {
            var uploadOptions = {};
            uploadOptions['url'] = 'config-guide?processAction=uploadJDBCDriver';
            uploadOptions['title'] = 'Upload JDBC Driver';
            uploadOptions['nextFunction'] = function() {
                PWM_MAIN.gotoUrl('config-guide');
            };
            PWM_MAIN.IdleTimeoutHandler.cancelCountDownTimer();
            UILibrary.uploadFileDialog(uploadOptions);
        }


        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_VAR['originalHealthBody'] = PWM_MAIN.getObject('healthBody').innerHTML;
            clearHealthDiv();
            checkIfNextEnabled();

            PWM_MAIN.addEventHandler('configForm','input',function(){
                handleFormActivity();
            });

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('healthBody','click',function(){loadHealth()});

            PWM_MAIN.addEventHandler('button-uploadJDBCDriver','click',function(){
                uploadJDBCDriver();
            });
        });

        function checkIfNextEnabled() {
            if (PWM_GLOBAL['pwm-health'] === 'GOOD' || PWM_GLOBAL['pwm-health'] === 'CONFIG') {
                PWM_MAIN.getObject('button_next').disabled = false;
            } else {
                PWM_MAIN.getObject('button_next').disabled = true;
            }
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
