<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2010 The PWM Project
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

<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Locale" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body class="tundra">
<link href="<%=request.getContextPath()%>/resources/dojo/dojo/resources/dojo.css"
      rel="stylesheet" type="text/css"/>
<link href="<%=request.getContextPath()%>/resources/dojo/dijit/themes/tundra/tundra.css"
      rel="stylesheet" type="text/css"/>
<link href="<%=request.getContextPath()%>/resources/dojo/dojox/grid/enhanced/resources/tundraEnhancedGrid.css"
      rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dojo/dojo.js"
        djConfig="parseOnLoad: true"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dijit/dijit.js"
        djConfig="parseOnLoad: true"></script>
<script type="text/javascript">
    dojo.require("dojo.parser");
    dojo.require("dijit.layout.ContentPane");
    dojo.require("dijit.layout.TabContainer");
    dojo.require("dijit.form.Button");
    dojo.require("dijit.form.NumberTextBox");
    dojo.require("dijit.Dialog");
    dojo.require("dijit.TitlePane");

    function writeSetting(keyName, valueData) {
        var jsonData = { key:keyName, value:valueData };
        var jsonString = dojo.toJson(jsonData);

        var options =
        {
            url: "ConfigManager?processAction=writeSetting&formID=<pwm:FormID/>",
            postData: jsonString,
            contentType: "application/json;charset=utf-8",
            dataType: "json"
        };
        //Call the asynchronous xhrPost
        dojo.xhrPost(options);
    }

    function toggleBooleanSetting(keyName) {
        var valueElement = getObject('value_' + keyName);
        var buttonElement = getObject('button_' + keyName);
        var innerValue = valueElement.value;
        if (innerValue == 'true') {
            valueElement.value = 'false';
            buttonElement.innerHTML = ' False ';
        } else {
            valueElement.value = 'true';
            buttonElement.innerHTML = ' True ';
        }
    }

    function addLocalizedInputField(parentDiv, settingKey, localeString) {
        var newInputElement = document.createElement("input");
        newInputElement.setAttribute("name", settingKey);
        newInputElement.setAttribute("value", "element_value");
        var parentDivElement = getObject(parentDiv);
        parentDivElement.appendChild(newInputElement);
    }

    function readSetting(keyName, valueWriter, locale) {
        dojo.xhrGet({
            url:"ConfigManager?processAction=readSetting&key=" + keyName + "&formID=<pwm:FormID/>",
            handleAs:"json",
            timeout: 5000,
            error: function(errorObj) {
                alert("error loading " + keyName + ", reason: " + errorObj)
            },
            load: function(data){
                var resultValue = data.value;
                valueWriter(resultValue);
            }
        });

    }
</script>
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Settings"/></jsp:include>
    <form action="<pwm:url url='ConfigManager'/>" method="post" name="configManager" enctype="application/x-www-form-urlencoded"
          onsubmit="" onreset="handleFormClear();">
        <div id="centerbody" style="width: 700px">
            PWM Configurations are controlled by the configuration file <i>pwm-configuration.xml</i>.  This
            page can be used to edit the contents of that file.  You can input an existing <i>pwm-configratuion.xml</i>
            or create a new one from scratch.  Once you have completed the configuration, Generate a configuration file
            and save it in PWM's <i>WEB-INF</i> subdirectory.
            <br class="clear"/>
            <br class="clear" style="height:3px"/>

            <div id="mainTabContainer" dojoType="dijit.layout.TabContainer" class="tundra" doLayout="false"
                 style="width:700px">

                <%
                    for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory().keySet()) {
                        if (loopCategory == PwmSetting.Category.GENERAL) {
                            final List<PwmSetting> loopSettings = PwmSetting.valuesByCategory().get(loopCategory);
                %>
                <div id="<%=loopCategory%>" dojoType="dijit.layout.ContentPane" title="<%=loopCategory.getLabel(request.getLocale())%>">
                    <%= loopCategory.getDescription(request.getLocale())%>
                    <%  for (final PwmSetting loopSetting : loopSettings) { %>
                    <div dojoType="dijit.TitlePane" title="<%= loopSetting.getLabel(request.getLocale()) %> [<%= loopSetting.getKey() %>]">
                        <%= loopSetting.getDescription(request.getLocale()) %>
                        <br class="clear"/>
                        <% if (loopSetting.isLocalizable()) { %>
                        <div id="setting_<%=loopSetting.getKey()%>_i18n_list">
                            <label>Default</label>
                            <input name="setting_<%=loopSetting.getKey()%>" size="60"
                                   value="nyet!"/>
                        </div>
                        <select name="setting_<%=loopSetting.getKey()%>" onclick="">
                            <option value="">Add Locale</option>
                            <option value="">----------</option>
                            <% for (final Locale locale : Locale.getAvailableLocales()) { %>
                            <option value="" onclick="addLocalizedInputField('setting_<%=loopSetting.getKey()%>_i18n_list','setting_<%=loopSetting.getKey()%>_<%=locale.getCountry()%>','french');"><%=locale.getDisplayName()%></option>
                            <% } %>
                        </select>
                        <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.BOOLEAN) { %>
                        <input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
                        <button id="button_<%=loopSetting.getKey()%>" dojoType="dijit.form.Button" type="button"
                                onclick="toggleBooleanSetting('<%=loopSetting.getKey()%>');writeSetting('<%=loopSetting.getKey()%>', getObject('value_' + '<%=loopSetting.getKey()%>').value);">
                            Loading...
                        </button>
                        <script type="text/javascript">
                            readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
                                var valueElement = getObject('value_' + '<%=loopSetting.getKey()%>');
                                var buttonElement = getObject('button_' + '<%=loopSetting.getKey()%>');
                                if (dataValue == 'true') {
                                    valueElement.value = 'true';
                                    buttonElement.innerHTML = ' True ';
                                } else {
                                    valueElement.value = 'false';
                                    buttonElement.innerHTML = ' False ';
                                }
                            },null);
                        </script>
                        <% } else { %>
                        <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"
                               value="[LOADING...]" onchange="writeSetting('<%=loopSetting.getKey()%>',this.value);"
                                <% if (loopSetting.getSyntax() == PwmSetting.Syntax.TEXT) { %>
                               size="100"
                                <% } %>
                                <% if (loopSetting.getSyntax() == PwmSetting.Syntax.PASSWORD) { %>
                               type="password" autocomplete="off" size="60"
                                <% } %>
                                <% if (loopSetting.getSyntax() == PwmSetting.Syntax.NUMERIC) { %>
                               size="30" dojoType="dijit.form.NumberTextBox" constraints="{min:<%=loopSetting.getMinimumValue()%>,max:<%=loopSetting.getMaximumValue()%>,places:0}" invalidMessage="invalid value"
                                <% } %>
                                />
                        <script type="text/javascript">
                            readSetting('<%=loopSetting.getKey()%>', function(dataValue) {getObject('value_<%=loopSetting.getKey()%>').value = dataValue;},null);
                        </script>
                        <% } %>
                    </div>
                    <% } %>
                </div>
                <% } %>
                <% } %>
            </div>
            <br class="clear"/>
            <input tabindex="3" type="submit" class="btn"
                   name="generate"
                   value="   Generate Configuration File  "
                   id="generateBtn"/>
            <br class="clear"/>
        </div>
    </form>
</div>
<br class="clear"/>
footer
</body>
</html>

