<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<% PwmSetting.Template selectedTemplate = configGuideBean.getSelectedTemplate(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:Display key="Title_ConfigGuide_template" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <pwm:Display key="Display_ConfigGuideSelectTemplate" bundle="Config"/>
        <br/>
        <form id="configForm" data-dojo-type="dijit/form/Form">
            <select id="<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>" name="<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>"
                    onchange="formHandler()" data-dojo-type="dijit/form/Select" style="width:300px">
                <% if (selectedTemplate == null) { %>
                <option value="NOTSELECTED" selected="selected">-- Please select a template --</option>
                <% } %>
                <% for (final PwmSetting.Template loopTemplate : PwmSetting.Template.values()) { %>
                <% boolean selected = loopTemplate.equals(selectedTemplate); %>
                <option value="<%=loopTemplate.toString()%>"<% if (selected) { %> selected="selected"<% } %>>
                    <%=loopTemplate.getLabel(pwmSessionHeader.getSessionStateBean().getLocale())%>
                </option>
                <% } %>
            </select>
        </form>
            <br/>

            <div id="buttonbar">
                <button class="btn" id="button_previous" onclick="PWM_GUIDE.gotoStep('START');">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                    <pwm:Display key="Button_Previous" bundle="Config"/>
                </button>
                &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                <button class="btn" id="button_next" onclick="PWM_GUIDE.gotoStep('LDAP');">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:Display key="Button_Next" bundle="Config"/>
                </button>
            </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function formHandler() {
            var startTemplate = '<%=selectedTemplate == null ? "" : selectedTemplate.toString()%>';

            var configForm = dojo.formToObject('configForm');
            var newTemplate = configForm['<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>'];
            if (startTemplate && startTemplate.length > 0 && startTemplate != newTemplate) {
                PWM_MAIN.showConfirmDialog({
                    text:'Changing the template will cause existing guide settings to be cleared.  Are you sure you wish to continue?',
                    okAction:function(){
                        PWM_GUIDE.updateForm();
                        updateNextButton();
                    },
                    cancelAction:function(){
                        PWM_MAIN.goto('/private/config/ConfigGuide');
                    }
                });
            } else {
                PWM_GUIDE.updateForm();
                updateNextButton();
            }
        }

        function updateNextButton() {
            require(["dojo"],function(dojo) {
                var configForm = dojo.formToObject('configForm');
                var notSelected = configForm['<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>'] == 'NOTSELECTED';
                PWM_MAIN.getObject('button_next').disabled = notSelected;
            });
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox","dijit/form/Select"],function(dojoParser){
                dojoParser.parse();
                updateNextButton();
            });
        });
    </script>
</pwm:script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/admin.js"/>"></script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
