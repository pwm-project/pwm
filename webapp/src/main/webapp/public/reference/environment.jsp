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
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.config.PwmSettingCategory" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<style nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" type="text/css">
    <!--
    .codeExample {
        background-color: #5d5d5d;
        color: white;
        border: 1px white solid;
        margin: 5px;
        padding: 10px;
    }
    -->
</style>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Environment Reference"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="reference-nav.jsp"%>
        <h1><%=PwmConstants.PWM_APP_NAME%> Environment</h1>
        <h2><code>ApplicationPath</code></h2>
        <p>The application path setting is required.  Configure the application path to use a directory on the local filesystem.  <%=PwmConstants.PWM_APP_NAME%> will store it's
            operating files in this directory.  Specifically, the following contents will be stored in the application path:</p>
        <ul>
            <li>Configuration</li>
            <li>LocalDB database</li>
            <li>Log files</li>
            <li>Backup data</li>
        </ul>
        <p>
            The path value is given as an absolute directory path on the local file system.  The <%=PwmConstants.PWM_APP_NAME%> application must have full permissions to create, modify and delete folders in this directory.  The directory must already be exist when <%=PwmConstants.PWM_APP_NAME%> is started, it will not be
            automatically created.
        </p>
        <p>
            Older versions of <%=PwmConstants.PWM_APP_NAME%> did not require the application path to be set and would automatically use the exploded war directory's
            <code>WEB-INF</code> directory as the application path.  This is no longer done automatically, and having the application path be within the exploded war or anywhere in the application server's directory structure is
            not recommended.
        </p>
        <h3>Setting the Application Path</h3>
        The following configuration methods are available:
        <ul>
            <li><a href="#envvar">Environment Variable (Recommended)</a></li>
            <li><a href="#property">Java System Property</a></li>
            <li><a href="#webxml">Servlet <code>web.xml</code></a></li>
        </ul>
        <h3><a id="envvar">Environment Variable (Recommended)</a></h3>
        <p>The application will read the <b><%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH</b> variable to determine the location of the application path.  Relative paths are not permitted.</p>
        <p>Because you set this value at the OS level, it will make maintaining and updating the application easier because you will not need to change anything other than applying a new <code>war</code> file.</p>
        <h3>Linux Example</h3>
        <div class="codeExample">
            <code>export <%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH='/home/user/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data'</code>
        </div>
        <p>This environment variable would typically be set as part of an init or other script file that starts your application server.</p>
        <h4>Windows Example</h4>
        <div class="codeExample">
            <code>set "<%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH=c:\<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data"</code>
        </div>
        <p>This environment variable is typically set as part of a <code>.bat</code> file that starts your application server, or possibly as a system-wide environment variable via the windows control panel.</p>
        <br/>
        <h3><a id="property">Java System Property</a></h3>
        <p>The application will read the java system property <b><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath</b> variable to determine the location of
            the application path.  Relative paths are not permitted.  These example parameters would be added to the java command
            line that starts the application server (tomcat) and java process.</p>
        <h4>Linux Example</h4>
        <div class="codeExample">
            <code>-D<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath='/home/user/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data'</code>
        </div>
        <h4>Windows Example</h4>
        <div class="codeExample">
            <code>-D<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath="c:\<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data"</code>
        </div>
        <h3><a id="webxml">Servlet web.xml</a></h3>
        Modify the servlet <code>WEB-INF/web.xml</code> file.  You must modify the application war file to accomplish this method.  This method accommodates multiple
        applications on a single application server.  File paths must be absolute.
        <h4>Linux Example</h4>
        <div class="codeExample">
            <code>
                &lt;context-param&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;description&gt;...&lt;/description&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-name&gt;applicationPath&lt;/param-name&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-value&gt;/home/user/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data&lt;/param-value&gt;<br/>
                &lt;/context-param&gt;<br/>
            </code>
        </div>
        <h4>Windows Example</h4>
        <div class="codeExample">
            <code>
                &lt;context-param&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;description&gt;...&lt;/description&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-name&gt;applicationPath&lt;/param-name&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-value&gt;c:\<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data&lt;/param-value&gt;<br/>
                &lt;/context-param&gt;<br/>
            </code>
        </div>
        <h3>Linux Example</h3>
        <div class="codeExample">
            <code>export <%=PwmConstants.PWM_APP_NAME%>_APPLICATIONFLAGS='ManageHttps,NoFileLock'</code>
        </div>
        <br/>
        <h1>Environment Variable Names and Servlet Context</h1>
        <p>
            The environment variables listed above, such as <code><%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH</code> and <code><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath</code> assume the default context name of
            <code>/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>/</code> is used.  The context path will default to the war file name on most systems.
        </p>
        <p>
            For non-default context names, or when there are multiple <%=PwmConstants.PWM_APP_NAME%> applications deployed on the same server, the environment variable must include the context.  For example:
        </p>
        <table>
            <tr>
                <td><h3>Context</h3></td><td><h3>Environment Property Name</h3></td><td><h3>Environment Variable Name</h3></td>
            </tr>
            <tr>
                <td><code>/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>/</code></td><td><code><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath</code><br/>or<br/><code><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath</code></td><td><code><%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH</code><br/>or<br/><code><%=PwmConstants.PWM_APP_NAME%>_<%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH</code></td>
            </tr>
            <tr>
                <td><code>/acme/</code></td><td><code><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.acme.applicationPath</code></td><td><code><%=PwmConstants.PWM_APP_NAME%>_ACME_APPLICATIONPATH</code></td>
            </tr>
            <tr>
                <td><code>/acme2/</code></td><td><code><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.acme2.applicationPath</code></td><td><code><%=PwmConstants.PWM_APP_NAME%>_ACME2_APPLICATIONPATH</code></td>
            </tr>
        </table>
        <p>In case of conflict, the application path parameters are evaluated in the following order.</p>
        <ol>
            <li><a href="#webxml">Servlet <code>web.xml</code></a></li>
            <li><a href="#property">Java System Property</a></li>
            <li><a href="#envvar">Environment Variable (Recommended)</a></li>
        </ol>
        <h2><code>ApplicationFlags</code></h2>
        <p>Application flags can be set to enable or disable behaviors in <%=PwmConstants.PWM_APP_NAME%>.   By default, no flags are set.  Setting flags is optional.  Flags are specified as a comma seperated
            list of values.  Values are case sensitive.  In most cases, you will not need to set an application flag.
        <table>
            <tr><td><h3>Flag</h3></td><td><h3>Behavior</h3></td></tr>
            <tr><td>ManageHttps</td><td>Enable the setting category <code><%=PwmSettingCategory.HTTPS_SERVER.getLabel(PwmConstants.DEFAULT_LOCALE)%></code></td></tr>
            <tr><td>NoFileLock</td><td>Disable the file lock in the application path directory.</td></tr>
        </table>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
<br/>
<br/>
</body>
</html>
