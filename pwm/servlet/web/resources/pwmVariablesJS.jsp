<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2012 The PWM Project
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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.Display" %><%@ page import="java.util.Collections"%><%@ page import="java.util.Locale"%><%@ page import="java.util.ResourceBundle"%><%@ page import="java.util.TreeSet"%>
        <% final PwmSession pwmSession = PwmSession.getPwmSession(session); %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% response.setDateHeader("Expires", System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000)); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/javascript; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
PWM_GLOBAL={};
PWM_STRINGS={};
function initPwmVariables() {
    initPwmGlobalValues();
    initPwmStringValues();
    initPwmLocaleVars();
}

function initPwmStringValues() {
    <% final ResourceBundle bundle = ResourceBundle.getBundle(Display.class.getName()); %>
    <% for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) { %>
    PWM_STRINGS['<%=key%>']='<%=StringEscapeUtils.escapeJavaScript(Display.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),key,pwmApplication.getConfig()))%>';
    <% } %>
}

function initPwmGlobalValues() {
<% if (pwmApplication.getConfig() != null) { %>
    PWM_GLOBAL['setting-showHidePasswordFields'] = <%=pwmApplication.getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS)%>;
<% } %>
    PWM_GLOBAL['url-logout'] = "<%=request.getContextPath()%><pwm:url url='/public/Logout?idle=true'/>";
    PWM_GLOBAL['url-command'] = "<%=request.getContextPath()%><pwm:url url='/public/CommandServlet'/>";
    PWM_GLOBAL['url-resources'] = "<%=request.getContextPath()%><pwm:url url='/resources'/>";
    PWM_GLOBAL['url-restservice'] = "<%=request.getContextPath()%><pwm:url url='/public/rest'/>";
    PWM_GLOBAL['url-setupresponses'] = '<pwm:url url='SetupResponses'/>';
}

function initPwmLocaleVars() {
    var localeInfo = {};
<% for (final Locale loopLocale : pwmApplication.getConfig().getKnownLocales()) { %>
<% if ("".equals(loopLocale.toString())) { %>
    createCSSClass('.flagLang_en','background-image: url(flags/languages/en.png)');
<% } else { %>
    createCSSClass('.flagLang_<%=loopLocale.toString()%>','background-image: url(flags/languages/<%=loopLocale.toString()%>.png)');
<% } %>
    localeInfo['<%=loopLocale.toString()%>'] = '<%=loopLocale.getDisplayName()%>';
<% } %>
    PWM_GLOBAL['localeInfo'] = localeInfo;
}

function createCSSClass(selector, style)
{
    // using information found at: http://www.quirksmode.org/dom/w3c_css.html
    // doesn't work in older versions of Opera (< 9) due to lack of styleSheets support
    if(!document.styleSheets) return;
    if(document.getElementsByTagName("head").length == 0) return;
    var stylesheet;
    var mediaType;
    if(document.styleSheets.length > 0)
    {
        for(i = 0; i<document.styleSheets.length; i++)
        {
            if(document.styleSheets[i].disabled) continue;
            var media = document.styleSheets[i].media;
            mediaType = typeof media;
            // IE
            if(mediaType == "string")
            {
                if(media == "" || media.indexOf("screen") != -1)
                {
                    styleSheet = document.styleSheets[i];
                }
            }
            else if(mediaType == "object")
            {
                if(media.mediaText == "" || media.mediaText.indexOf("screen") != -1)
                {
                    styleSheet = document.styleSheets[i];
                }
            }
            // stylesheet found, so break out of loop
            if(typeof styleSheet != "undefined") break;
        }
    }
    // if no style sheet is found
    if(typeof styleSheet == "undefined")
    {
        // create a new style sheet
        var styleSheetElement = document.createElement("style");
        styleSheetElement.type = "text/css";
        // add to <head>
        document.getElementsByTagName("head")[0].appendChild(styleSheetElement);
        // select it
        for(i = 0; i<document.styleSheets.length; i++)
        {
            if(document.styleSheets[i].disabled) continue;
            styleSheet = document.styleSheets[i];
        }
        // get media type
        var media = styleSheet.media;
        mediaType = typeof media;
    }
    // IE
    if(mediaType == "string")
    {
        for(i = 0;i<styleSheet.rules.length;i++)
        {
            // if there is an existing rule set up, replace it
            if(styleSheet.rules[i].selectorText.toLowerCase() == selector.toLowerCase())
            {
                styleSheet.rules[i].style.cssText = style;
                return;
            }
        }
        // or add a new rule
        styleSheet.addRule(selector,style);
    }
    else if(mediaType == "object")
    {
        for(i = 0;i<styleSheet.cssRules.length;i++)
        {
            // if there is an existing rule set up, replace it
            if(styleSheet.cssRules[i].selectorText.toLowerCase() == selector.toLowerCase())
            {
                styleSheet.cssRules[i].style.cssText = style;
                return;
            }
        }
        // or insert new rule
        styleSheet.insertRule(selector + "{" + style + "}", styleSheet.cssRules.length);
    }
}

initPwmVariables();
