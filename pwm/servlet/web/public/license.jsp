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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<head>
    <title>Licenses</title>
    <link href="<%=request.getContextPath()%>/public/resources/style.css" rel="stylesheet" type="text/css" media="screen"/>
    <link href="<%=request.getContextPath()%>/public/resources/dojo/dijit/themes/nihilo/nihilo.css" rel="stylesheet" type="text/css"/>
    <script type="text/javascript">var PWM_GLOBAL = PWM_GLOBAL || {};var PWM_STRINGS = PWM_STRINGS || {};PWM_GLOBAL['startupFunctions'] = new Array();</script>
    <script defer data-dojo-config="async: true" type="text/javascript" src="<%=request.getContextPath()%>/public/resources/dojo/dojo/dojo.js"></script>
    <script defer type="text/javascript" src="<%=request.getContextPath()%>/public/resources/js/main.js"></script>
    <script defer type="text/javascript" src="<%=request.getContextPath()%>/public/jsClientValues.jsp"></script>
</head>
<body onload="pwmPageLoadHandler()" class="nihilo">
<div id="wrapper">
    <div id="centerbody">
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h1>PWM License</h1>
            <a href="http://code.google.com/p/pwm">http://code.google.com/p/pwm</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/gpl-2_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Apache Commons</h2>
            <a href="http://commons.apache.org/">http://commons.apache.org/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/apache20_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Apache Derby</h2>
            <a href="http://db.apache.org/derby/">http://db.apache.org/derby/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/apache20_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Apache HttpComponents</h2>
            <a href="http://hc.apache.org/">http://hc.apache.org/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/apache20_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Apache Log4j</h2>
            <a href="http://logging.apache.org/log4j/1.2/">http://logging.apache.org/log4j/1.2/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/apache20_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>CAS</h2>
            <a href="http://www.jasig.org/cas">http://www.jasig.org/cas</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/ja-sig_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Dojo</h2>
            <a href="http://dojotoolkit.org/">http://dojotoolkit.org/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/dojo_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>dgrid</h2>
            <a href="http://dojofoundation.org/packages/dgrid/">http://dojofoundation.org/packages/dgrid/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/dgrid_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Gson</h2>
            <a href="http://code.google.com/p/google-gson/">http://code.google.com/p/google-gson/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/apache20_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>jBcrypt</h2>
            <a href="http://www.mindrot.org/projects/jBCrypt/">http://www.mindrot.org/projects/jBCrypt/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/jbCrypt_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>JDOM</h2>
            <a href="http://www.jdom.org/">http://www.jdom.org/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/jdom_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Jersey</h2>
            <a href="http://jersey.java.net/">http://jersey.java.net/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/glassfish_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>LDAPChai</h2>
            <a href="https://code.google.com/p/ldapchai/">https://code.google.com/p/ldapchai/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/lgpl-3.0_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>OpenLDAP</h2>
            <a href="http://www.opeldap.org/jldap/">http://www.openldap.org/jldap/</a>
            <br/>
            <a href="http://www.novell.com/developer/ndk/ldap_classes_for_java.html">http://www.novell.com/developer/ndk/ldap_classes_for_java.html</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/openldap_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Oracle Berkeley DB Java Edition</h2>
            <a href="http://www.oracle.com/technetwork/products/berkeleydb/overview/index-093405.html">http://www.oracle.com/technetwork/products/berkeleydb/overview/index-093405.html</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/berkelyDbJava_license.txt"/></pre>
            </div>
            <br/>
        </div>
        <div style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
            <h2>Syslog4j</h2>
            <a href="http://www.syslog4j.org/">http://www.syslog4j.org/</a>
            <br/><br/>
            <div style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'License',open: false">
                <pre><jsp:include page="license/lgpl-2.1-license.txt"/></pre>
            </div>
            <br/>
        </div>
    </div>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_GLOBAL['idle_suspendTimeout'] = true;
            require(["dojo/parser","dijit/TitlePane"],function(dojoParser){
                dojoParser.parse();
            });
        });
    </script>
</body>
</html>
