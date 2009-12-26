<%--
  ~ This file is imported by most JSPs, it shows the main 'header' in the html
  - which by default is a blue-gray gradieted and rounded block.
  -
  - All styles are local (does not use any css styles in pwmStyle.css) to make
  - customizations easier.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<%-- older, pwm v1.4 style block header
<br/>
<div style="width: 600px; margin-left: auto; margin-right: auto; height:70px; background-image:url('<%=request.getContextPath()%>/resources/<pwm:url url='header.gif'/>');">
    <div style="width:600px; padding-top:9px; margin-left:10px; font-family:Trebuchet MS, sans-serif; font-size:22px; color:#FFFFFF;">
        <pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/>
     </div>
    <div style="width:600px; margin-left: 10px; margin-top: 3px; font-family:Trebuchet MS, sans-serif; font-size:14px; color:#FFFFFF;">
        <pwm:Display key="APPLICATION-TITLE"/>
     </div>
</div>
--%>
<%-- new "full-width" header style.  --%>
<div style="width: 100%; height: 70px; margin: 0; background-image:url('<%=request.getContextPath()%>/resources/<pwm:url url='header-gradient.gif'/>')">
    <div style="width:600px; padding-top:9px; margin-left: auto; margin-right: auto; font-family:Trebuchet MS, sans-serif; font-size:22px; color:#FFFFFF;">
        <pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/>
     </div>
    <div style="width:600px; margin: auto; font-family:Trebuchet MS, sans-serif; font-size:14px; color:#FFFFFF;">
        <pwm:Display key="APPLICATION-TITLE"/>
     </div>
</div>
