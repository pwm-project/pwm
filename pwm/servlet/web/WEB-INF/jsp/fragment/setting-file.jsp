<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.value.FileValue" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.bean.ConfigManagerBean" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Map" %>
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

<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSetting loopSetting = (PwmSetting)request.getAttribute("setting"); %>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request,response); %>
<% final ConfigManagerBean configManagerBean = pwmRequest.getPwmSession().getConfigManagerBean(); %>
<% final Map<FileValue.FileInformation, FileValue.FileContent> files = (Map<FileValue.FileInformation, FileValue.FileContent>)request.getAttribute("file"); %>
<% final boolean isDefault = configManagerBean.getConfiguration().isDefaultValue(loopSetting); %>
<% for (FileValue.FileInformation fileInformation : files.keySet()) {%>
<% final FileValue.FileContent fileContent = files.get(fileInformation); %>

<table style="width:100%" id="table_<%=loopSetting.getKey()%>">
    <tr><td>Name</td><td><%=fileInformation.getFilename()%></td></tr>
    <tr><td>Type</td><td><%=fileInformation.getFiletype()%></td></tr>
    <tr><td>Size</td><td><%=NumberFormat.getInstance(pwmRequest.getLocale()).format(fileContent.size())%> bytes</td></tr>
    <tr><td>Md5sum</td><td><%=fileContent.md5sum()%></td></tr>
</table>
<% } %>
<% if (files.isEmpty()) { %>
No file stored<br/><br/>
<% } else { %>
<button id="<%=loopSetting.getKey()%>_RemoveButton">Remove File</button>
<% } %>
<button id="<%=loopSetting.getKey()%>_UploadButton">Upload File</button>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/form/Button"],function(parser,Button){
                new Button({
                    onClick:function(){FileSettingHandler.uploadFile('<%=loopSetting.getKey()%>') }
                },'<%=loopSetting.getKey()%>_UploadButton');
                new Button({
                    onClick:function(){
                        PWM_MAIN.showConfirmDialog({text:'Are you sure you want to remove the currently stored file?',okAction:function(){
                            PWM_CFGEDIT.resetSetting('<%=loopSetting.getKey()%>');
                            PWM_MAIN.showWaitDialog({loadFunction:function(){
                                PWM_MAIN.goto(window.location.pathname);
                            }});
                        }});
                    }
                },'<%=loopSetting.getKey()%>_RemoveButton');
                PWM_CFGEDIT.updateSettingDisplay('<%=loopSetting.getKey()%>',<%=isDefault%>);
            });
        });
    </script>
</pwm:script>

