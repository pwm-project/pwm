<%--
  ~ This file is imported by most JSPs, it shows the main 'header' in the html
  - which by default is a blue-gray gradieted and rounded block.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<div id="header">
    <div id="header-page">
        <pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/>
     </div>
    <div id="header-title">
        <pwm:Display key="APPLICATION-TITLE"/>
     </div>
</div>
