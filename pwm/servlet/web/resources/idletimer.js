/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

var SETTING_LOOP_FREQUENCY = 1000;
var SETTING_PING_FREQUENCY = 10;
var SETTING_WARN_SECONDS = 30;

var dateFuture = new Date();
var idleTimeout = 0;
var sendPing = false;
var lastPingTime = 0;
var warningDisplayed = false;

function initCountDownTimer(secondsRemaining) {
    idleTimeout = secondsRemaining;
    dateFuture = new Date(new Date().getTime() + (secondsRemaining * 1000));
    lastPingTime = new Date().getTime();
    resetIdleCounter();
    setInterval("pollActivity()", SETTING_LOOP_FREQUENCY); //poll scrolling
    document.onclick = resetIdleCounter;
    document.onkeydown = resetIdleCounter;
}

function resetIdleCounter() {
    var idleSeconds = calcIdleSeconds();
    closeIdleWarning();
    getObject("idle_status").firstChild.nodeValue = makeIdleDisplayString(idleSeconds);

    dateFuture = new Date(new Date().getTime() + (idleTimeout * 1000));
    {
        var dateNow = new Date().getTime();
        var amount = dateNow - lastPingTime;

        if (amount > SETTING_PING_FREQUENCY * 1000) { //calc milliseconds between dates
            pingServer();
            lastPingTime = dateNow;
            sendPing = false;
        }
    }

    warningDisplayed = false;
}

function pollActivity() {
    var idleSeconds = calcIdleSeconds();
    var idleDisplayString = makeIdleDisplayString(idleSeconds);
    var idleStatusFooter = getObject("idle_status");
    if (idleStatusFooter != null) {
        idleStatusFooter.firstChild.nodeValue = idleDisplayString;
    }

    var warningDialogText = getObject("IdleDialogWindowIdleText");
    if (warningDialogText != null) {
        warningDialogText.firstChild.nodeValue = idleDisplayString;
    }

    if (idleSeconds < 0) {
        PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
        window.location = PWM_STRINGS['url-logout'];
    }

    if (idleSeconds < SETTING_WARN_SECONDS) {
        showIdleWarning();
    }
}

function pingServer() {
    var pingURL = PWM_STRINGS['url-command'] + "?processAction=idleUpdate&time=" + new Date().getTime() + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    dojo.xhrPost({
        url: pingURL
    });
}

function calcIdleSeconds() {
    var amount = dateFuture.getTime() - (new Date()).getTime(); //calc milliseconds between dates
    amount = Math.floor(amount / 1000); //kill the "milliseconds" so just secs
    return amount;
}

function makeIdleDisplayString(amount) {
    if (amount < 1) {
        return "";
    }

    var output = "";
    var days = 0, hours = 0, mins = 0, secs = 0;

    days = Math.floor(amount / 86400);

    amount = amount % 86400;
    hours = Math.floor(amount / 3600);

    amount = amount % 3600;
    mins = Math.floor(amount / 60);

    amount = amount % 60;
    secs = Math.floor(amount);

    // write number of days
    if (days != 0) {
        output += days + " ";
        if (days != 1) {
            output += PWM_STRINGS['Display_Days'];
        } else {
            output += PWM_STRINGS['Display_Day'];
        }
    }

    // write number of hours
    if (days != 0 || hours != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += hours + " ";
        if (hours != 1) {
            output += PWM_STRINGS['Display_Hours'];
        } else {
            output += PWM_STRINGS['Display_Hour'];
        }
    }

    // write number of minutes
    if (days != 0 || hours != 0 || mins != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += mins + " ";
        if (mins != 1) {
            output += PWM_STRINGS['Display_Minutes'];
        } else {
            output += PWM_STRINGS['Display_Minute'];
        }
    }

    // write number of seconds
    if (mins < 4) {
        if (output.length > 0) {
            output += ", ";
        }

        output += secs + " ";

        if (secs != 1) {
            output += PWM_STRINGS['Display_Seconds'];
        } else {
            output += PWM_STRINGS['Display_Second'];
        }
    }

    output = PWM_STRINGS['Display_IdleTimeout'] + " " + output;
    return output;
}

function showIdleWarning() {
    dojo.require("dijit.Dialog");
    if (!warningDisplayed) {
        warningDisplayed = true;

        var dialogBody = PWM_STRINGS['Display_IdleWarningMessage'] + '<br/><br/><span id="IdleDialogWindowIdleText">&nbsp;</span>';

        var theDialog = new dijit.Dialog({
            title: PWM_STRINGS['Display_IdleWarningTitle'],
            style: "width: 260px; border: 2px solid #D4D4D4;",
            content: dialogBody,
            closable: false,
            draggable: false,
            id: "idleDialog"

        });
        theDialog.setAttribute('class', 'tundra');
        theDialog.show();
    }
}

function closeIdleWarning() {
    var dialog = dijit.byId('idleDialog');
    if (dialog != null) {
        dialog.hide();
        dialog.destroyRecursive();
    }
}

