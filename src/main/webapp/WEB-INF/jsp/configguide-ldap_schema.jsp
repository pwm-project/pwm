<%@ page import="password.pwm.config.PwmSettingTemplate" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.ldap.schema.SchemaOperationResult" %>
<%@ page import="java.util.Set" %>
<%@ page import="javax.naming.event.EventDirContext" %>
<%@ page import="password.pwm.ldap.schema.SchemaExtender" %>
<%@ page import="password.pwm.ldap.schema.SchemaDefinition" %>
<%@ page import="java.util.List" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%
    ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);
    final Set<PwmSettingTemplate> templateSet =  ConfigGuideForm.generateStoredConfig(configGuideBean).getTemplateSet().getTemplates();
    boolean builtinExtenderAvailable = templateSet.contains(PwmSettingTemplate.NOVL) || templateSet.contains(PwmSettingTemplate.NOVL_IDM);
    boolean existingSchemaGood = false;
    String schemaActivityLog = "";
    try {
        SchemaOperationResult schemaManager = ConfigGuideServlet.extendSchema(configGuideBean,false);
        existingSchemaGood = schemaManager.isSuccess();
        schemaActivityLog = schemaManager.getOperationLog();
    } catch (Exception e) {
        schemaActivityLog = "unable to check schema: " + e.getMessage();
    }
%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <% if (builtinExtenderAvailable) { %>
        <form id="formData">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    LDAP Schema Status
                </div>
                <div class="setting_body">
                    <p>
                        <pwm:display key="Display_ConfigGuideLdapSchema" bundle="Config"/>
                    </p>
                    <div>
                        <div id="titlePane_<%=ConfigGuideForm.FormParameter.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                            <table style="width:100%">
                                <tr><td>Schema Detail</td></tr>
                                <tr><td class="setting_table_value"><pre><%=schemaActivityLog%></pre></td></tr>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            <br class="clear"/>
            <div id="outline_ldapcert-options" class="setting_outline">
                <div class="setting_title">LDAP Schema Extension</div>
                <div class="setting_body">
                    <p>
                        <% if (existingSchemaGood) { %>
                        Schema extension is complete.
                        <% } else { %>
                        <pwm:display key="Display_ConfigGuideLdapSchema2" bundle="Config"/>
                        <% } %>
                    </p>
                    <button class="btn" type="button" id="button-extendSchema" <%=existingSchemaGood?"disabled=\"disabled\"":""%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-bolt"></span></pwm:if>
                        Extend Schema
                    </button>
                </div>
            </div>
        </form>
        <% } else { %>
        <div class="setting_outline">
            <div class="setting_title">
                LDAP Schema
            </div>
            <div class="setting_body">
                <% final String ldapTemplateName = PwmSetting.TEMPLATE_LDAP.getOptions().get(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP)); %>
                <p>The storage location is set to <i>LDAP</i>, and the LDAP directory setting template is <i><%=ldapTemplateName%></i>.</p>
                <p>This configuration expects the LDAP server's schema to be extended or you can adjust the configuration to use pre-existing defined attributes in your LDAP directory.</p>
                <p>LDIF files to process the schema extension are included for several directory types.</p>
                <p>For reference, the standard schema definition is displayed below.</p>
                <%List<SchemaDefinition> schemaDefinitions = SchemaDefinition.getPwmSchemaDefinitions();%>
                <div class="overflow-panel-medium border">
                    <code>
                        <% for (SchemaDefinition defintion : schemaDefinitions) { %>
                        Type: <%=defintion.getSchemaType()%><br/>
                        Name: <%=defintion.getName()%><br/>
                        Definition: <%=defintion.getDefinition()%><br/>
                        <br/><br/>
                        <% } %>
                    </code>
                </div>
            </div>
        </div>
        <% } %>
        <br/>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});
            PWM_MAIN.addEventHandler('button-extendSchema','click',function(){PWM_GUIDE.extendSchema()});
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
