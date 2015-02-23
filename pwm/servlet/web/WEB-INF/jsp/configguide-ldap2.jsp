<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
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

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<% Map<String,String> DEFAULT_FORM = ConfigGuideServlet.defaultForm(configGuideBean.getStoredConfiguration().getTemplate()); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:display key="Title_ConfigGuide_ldap" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div class="setting_outline">
                <div class="setting_title">LDAP Contextless Login Root</div>
                <div class="setting_body">
                    Enter the top level LDAP context of your LDAP directory. This sets the top level LDAP container
                    where an LDAP sub-tree search is performed to find your user entries. If you need to enter multiple
                    containers, you can use the configuration editor to add them after this guide completes.
                    <br/><br/>
                    A default value has been generated but you may need to change it to be appropriate to your directory.
                    <div class="setting_item">
                        <b>LDAP Contextless Login Root</b>
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input class="configStringInput" id="<%=ConfigGuideServlet.PARAM_LDAP2_CONTEXT%>" name="<%=ConfigGuideServlet.PARAM_LDAP2_CONTEXT%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP2_CONTEXT))%>" placeholder="<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP2_CONTEXT)%>" required autofocus/>
                    </div>
                </div>
            </div>
            <br/>
            <div class="setting_outline">
                <div class="setting_title">
                    Administrator Search Filter
                </div>
                <div class="setting_body">
                    Enter the LDAP DN of the group used to determining if a user should be given administrator access to this system.  Any user
                    that is a member of this group will be granted administrator access.
                    <div class="setting_item">
                        <b>Administrator Group DN</b>
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input class="configStringInput" id="<%=ConfigGuideServlet.PARAM_LDAP2_ADMIN_GROUP%>" name="<%=ConfigGuideServlet.PARAM_LDAP2_ADMIN_GROUP%>" value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP2_ADMIN_GROUP))%>" placeholder="<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP2_ADMIN_GROUP)%>" required/>
                        <button type="button" id="button-viewAdminMatches" class="btn">
                            <span class="btn-icon fa fa-eye"></span>
                            View Matches
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
        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:display key="Button_Next"  bundle="Config"/>
            </button>
        </div>
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
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });
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
                        PWM_MAIN.showDialog({title:'Results',text:data['data']});
                    }
                };
                PWM_MAIN.ajaxRequest(url,loadFunction);
            }});
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
        PWM_MAIN.showWaitDialog();
        PWM_ADMIN.showAppHealth('healthBody', options);
    }
</script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
