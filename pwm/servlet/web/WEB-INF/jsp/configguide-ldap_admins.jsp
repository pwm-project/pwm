<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.Map" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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

<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% ConfigGuideBean configGuideBean = JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<% Map<ConfigGuideForm.FormParameter,String> PLACEHOLDER_FORM = ConfigGuideForm.placeholderForm(configGuideBean.getStoredConfiguration().getTemplate()); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div class="setting_outline">
                <div class="setting_title">
                    <pwm:display key="ldap_context_admin_title" bundle="ConfigGuide"/>
                </div>
                <div class="setting_body">
                    <pwm:display key="ldap_context_admin_description" bundle="ConfigGuide"/>
                    <div class="setting_item">
                        <b>Administrator Group DN</b>
                        <br/>
                        <input style="width:400px;" class="configStringInput" id="<%=ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP%>" name="<%=ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP))%>" placeholder="<%=PLACEHOLDER_FORM.get(ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP)%>" required/>
                        <button type="button" class="btn" id="button-browse-adminGroup">
                            <span class="btn-icon fa fa-sitemap"></span>
                            <pwm:display key="Button_Browse"/>
                        </button>
                        <button type="button" id="button-viewAdminMatches" class="btn">
                            <span class="btn-icon fa fa-eye"></span>
                            View Group Members
                        </button>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="healthBody" style="border:0; margin:0; padding:0; cursor: pointer">
            <div style="text-align: center">
                <button class="menubutton" style="margin-left: auto; margin-right: auto">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
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

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_VAR['originalHealthBody'] = PWM_MAIN.getObject('healthBody').innerHTML;
            checkIfNextEnabled();

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('configForm','input',function(){handleFormActivity()});
            PWM_MAIN.addEventHandler('healthBody','click',function(){loadHealth()});

            PWM_MAIN.addEventHandler('button-viewAdminMatches','click',function(){
                PWM_MAIN.showWaitDialog({loadFunction:function(){
                    var url = 'ConfigGuide?processAction=viewAdminMatches';
                    var loadFunction = function(data){
                        if (data['error']) {
                            PWM_MAIN.showErrorDialog(data);
                        } else {
                            var html = PWM_CONFIG.convertListOfIdentitiesToHtml(data['data']);
                            PWM_MAIN.showDialog({title:'Matches',text:html});
                        }
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction);
                }});
            });

            PWM_MAIN.addEventHandler('button-browse-adminGroup','click',function(){
                UILibrary.editLdapDN(function(value){
                    PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP%>').value = value;
                    handleFormActivity();
                })
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
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
