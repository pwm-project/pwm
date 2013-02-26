/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

function selectTemplate(template) {
    showWaitDialog('Loading...','',function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"InstallManager?processAction=selectTemplate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&template=" + template,
                preventCache: true,
                error: function(errorObj) {
                    showError("error starting configuration editor: " + errorObj);
                },
                load: function(result) {
                    if (!result['error']) {
                        setTimeout(function(){
                            window.location = "ConfigManager";
                        },30 * 1000);
                    } else {
                        showError(result['errorDetail']);
                    }
                }
            });
        });

    });
}

