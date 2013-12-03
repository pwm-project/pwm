<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.config.PwmSetting" %>
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

<% final PwmSetting loopSetting = (PwmSetting)request.getAttribute("setting"); %>
<% X509Certificate[] certificates = (X509Certificate[])request.getAttribute("certificate"); %>
<% final boolean hideActions = Boolean.parseBoolean((String)request.getAttribute("hideActions")); %>
<% for (X509Certificate certificate : certificates) {%>
<% final String md5sum = Helper.checksum(new ByteArrayInputStream(certificate.getEncoded()), "MD5"); %>
<% final String sha1sum = Helper.checksum(new ByteArrayInputStream(certificate.getEncoded()), "SHA1"); %>
<table style="width:100%" id="table_certificate0">
    <tr><td colspan="2" class="key" style="text-align: center">
        Certificate
    </td></tr>
    <tr><td>Subject Name</td><td><%=certificate.getSubjectX500Principal().getName()%></td></tr>
    <tr><td>Issuer Name</td><td><%=certificate.getIssuerX500Principal().getName()%></td></tr>
    <tr><td>Serial Number</td><td style="word-break: break-all"><%=certificate.getSerialNumber().toString(16).toUpperCase()%></td></tr>
    <tr><td>Validity</td><td>From <%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(certificate.getNotBefore())%>&nbsp;&nbsp; To <%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(certificate.getNotAfter())%></td></tr>
    <tr><td colspan="2" class="key" style="text-align: center; font-size: smaller">
        <a href="#" onclick="showCert_<%=md5sum%>()">details</a>
    </td></tr>
</table>
<script type="text/javascript">
    function showCert_<%=md5sum%>() {
        var body = '<pre style="white-space: pre-wrap; word-wrap: break-word">';
        body += 'md5sum: <%=md5sum%>\n';
        body += 'sha1sum: <%=sha1sum%>\n';
        body += '<%=StringEscapeUtils.escapeJavaScript(certificate.toString())%>';
        body += '</pre>'
        require(["dijit/Dialog"], function(Dialog){
            new Dialog({
                title: "Certificate Detail",
                content: body
            }).show();
        });
    }
</script>
<% } %>
<% if (!hideActions) { %>
<button id="<%=loopSetting.getKey()%>_ClearButton" data-dojo-type="dijit.form.Button">Clear</button>
<button id="<%=loopSetting.getKey()%>_AutoImportButton" data-dojo-type="dijit.form.Button">Import From LDAP Server</button>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/form/Button"],function(parser,Button){
            new Button({
                onClick:function(){handleResetClick('<%=loopSetting.getKey()%>') }
            },'<%=loopSetting.getKey()%>_ClearButton');
            new Button({
                onClick:function(){executeSettingFunction('<%=loopSetting.getKey()%>',preferences['profile'],'certificateImportFunction')}
            },'<%=loopSetting.getKey()%>_AutoImportButton');
        });
    });
</script>
<% } %>
