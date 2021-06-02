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


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.configmanager.ConfigManagerCertificatesServlet" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<link href="<pwm:url url='/public/resources/webjars/dgrid/css/dgrid.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <% if ((Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigHasCertificates)) { %>
        <div id="certDebugGrid" class="grid">
        </div>
        <% } else { %>
        <p>No certificates are present in the active configuration.</p>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">

        PWM_GLOBAL['startupFunctions'].push(function () {
            var PWM_CERT_FUNCTION = {};
            PWM_CERT_FUNCTION.certDebugHeaders = function() {
                return {
                    "subject":"Subject",
                    "menuLocation":"Configuration Setting",
                    "algorithm":"Algorithm",
                    "serial":"Serial",
                    "issueDate":"Issue Date",
                    "expirationDate":"Expiration Date"
                };
            };

            PWM_CERT_FUNCTION.initCertDebugGrid=function() {
                var headers = PWM_CERT_FUNCTION.certDebugHeaders();

                require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
                    function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
                        var columnHeaders = headers;

                        // Create a new constructor by mixing in the components
                        var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

                        // Now, create an instance of our custom grid
                        PWM_VAR['certDebugGrid'] = new CustomGrid({
                            columns: columnHeaders
                        }, "certDebugGrid");


                        PWM_CERT_FUNCTION.refreshCertDebugGrid();

                        PWM_VAR['certDebugGrid'].on(".dgrid-row:click", function(evt){
                            var row = PWM_VAR['certDebugGrid'].row(evt);
                            var text = '<pre>' + row.data.detail + '</pre>';
                            PWM_MAIN.showDialog({title:'Certificate Detail',text:text,showClose:true,showOk:false,dialogClass:'wide'});
                        });
                    });
            };

            PWM_CERT_FUNCTION.refreshCertDebugGrid=function() {
                require(["dojo"],function(dojo){
                    var grid = PWM_VAR['certDebugGrid'];
                    grid.refresh();

                    var url = 'certificates?processAction=certificateData';
                    var loadFunction = function(data) {
                        grid.renderArray(data['data']);
                        grid.set("sort", { attribute : 'expirationTime', ascending: true, descending: false });
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
                });
            };

            PWM_CERT_FUNCTION.initCertDebugGrid();
        });

    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
