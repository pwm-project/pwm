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


<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideServlet" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideUtils" %>

<% final ConfigGuideBean headerCgb = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<% final float pctComplete = ConfigGuideUtils.stepProgress(headerCgb.getStep()).asFloat(); %>
<div id="header" class="configguide-header">
    <div id="header-company-logo">
    </div>
    <div id="header-balance-div">
        <br/></div>
    <div id="header-right-logo">
    </div>
    <div id="header-center">
        <div id="header-center-left">
            <div id="header-title"><pwm:display key="title" bundle="ConfigGuide"/></div>
        </div>
    </div>
</div>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/configeditor.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<progress style="opacity: 0.5; width:96%; height:7px; padding: 0; margin: 0 1%;" value="<%=pctComplete%>" max="100"></progress>
