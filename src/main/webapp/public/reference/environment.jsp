<%@ page import="password.pwm.http.JspUtility" %>
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
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<style nonce="<pwm:value name="cspNonce"/>" type="text/css">
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
        <h1>About the <%=PwmConstants.PWM_APP_NAME%> Environment</h1>
        <p>
            The <%=PwmConstants.PWM_APP_NAME%> application requires environment settings to load and run successfully.  Specifically, an <b>application path</b> setting
            is required.  The path value is given as a directory on the local file system.  The pwm application must have full permissions to create, modify
            and delete folders in this directory.
        </p>
        <p>
            Previous versions of <%=PwmConstants.PWM_APP_NAME%> did not require the application path to be set and would automatically use the exploded war directory's
            <code>WEB-INF</code> directory as the application path.  This is no longer done automatically.
        </p>
        <h1>Setting the Application Path</h1>
        These methods are used in this order until a value is found.  The directory must already exist, and the application must have read and write privileges to that directory.
        <ol>
            <li><a href="#webxml">Servlet <code>web.xml</code></a></li>
            <li><a href="#property">Java System Property</a></li>
            <li><a href="#envvar">Environment Variable</a></li>
        </ol>
        <h2><a id="webxml">Servlet web.xml</a></h2>
        Modify the servlet <code>WEB-INF/web.xml</code> file.  You will need to modify the application war file to accomplish this method.  This method also accommodates multiple
        applications on a single application server.  File paths may be absolute or relative to the base of the exploded application directory.
        <h3>Linux</h3>
        <div class="codeExample">
            <code>
                &lt;context-param&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;description&gt;...&lt;/description&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-name&gt;applicationPath&lt;/param-name&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-value&gt;/home/user/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data&lt;/param-value&gt;<br/>
                &lt;/context-param&gt;<br/>
            </code>
        </div>
        <h3>Windows</h3>
        <div class="codeExample">
            <code>
                &lt;context-param&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;description&gt;...&lt;/description&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-name&gt;applicationPath&lt;/param-name&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-value&gt;c:\<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data&lt;/param-value&gt;<br/>
                &lt;/context-param&gt;<br/>
            </code>
        </div>
        <h3>Relative Location</h3>
        In this example a relative path is specified.  Setting the value to <code>WEB-INF</code> mimics the behavior of older versions of the application.
        <div class="codeExample">
            <code>
                &lt;context-param&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;description&gt;...&lt;/description&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-name&gt;applicationPath&lt;/param-name&gt;<br/>
                &nbsp;&nbsp;&nbsp;&lt;param-value&gt;WEB-INF&lt;/param-value&gt;<br/>
                &lt;/context-param&gt;<br/>
            </code>
        </div>
        <h2><a id="property">Java System Property</a></h2>
        <p>The application will read the java system property <b><%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath</b> variable to determine the location of
            the application path.  Relative paths are not permitted.  These example parameters would be added to the java command
            line that starts the application server (tomcat) and java process.</p>
        <h3>Linux</h3>
        <div class="codeExample">
            <code>-D<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath='/home/user/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data'</code>
        </div>
        <h3>Windows</h3>
        <div class="codeExample">
            <code>-D<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>.applicationPath="c:\<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data"</code>
        </div>
        <h2><a id="envvar">Environment Variable</a></h2>
        <p>The application will read the <b><%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH</b> variable to determine the location of the application path.  Relative paths are not permitted.</p>
        <h3>Linux</h3>
        <div class="codeExample">
            <code>export <%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH='/home/user/<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data'</code>
        </div>
        <h3>Windows</h3>
        <div class="codeExample">
            <code>set "<%=PwmConstants.PWM_APP_NAME%>_APPLICATIONPATH=c:\<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-data"</code>
        </div>
        <br/><br/><br/><br/>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
