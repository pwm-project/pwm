<%--
  ~ This file is imported by most JSPs, it shows the main 'header' inthe html
  - which by default is a blue-gray gradieted and rounded block.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<div id="header" style="width: 600px; margin-left: auto; margin-right: auto">
    <p class="logotext"><pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/><br/>
        <span class="logotext2"><pwm:Display key="APPLICATION-TITLE"/></span>
    </p>
</div>
