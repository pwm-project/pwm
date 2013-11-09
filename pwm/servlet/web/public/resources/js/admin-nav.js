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

function buildMenuBar() {
    require(["dojo","dijit","dijit/Menu","dijit/Dialog","dijit/MenuBar","dijit/MenuItem","dijit/MenuBarItem","dijit/PopupMenuBarItem","dijit/CheckedMenuItem","dijit/MenuSeparator","dojo/domReady!"],
        function(dojo,dijit,Menu,Dialog,MenuBar,MenuItem,MenuBarItem,PopupMenuBarItem,CheckedMenuItem,MenuSeparator){
            clearDijitWidget('topMenuBar');
            var topMenuBar = new MenuBar({id:"topMenuBar"});
            { // Status Menu
                var statusMenu = new Menu({});
                statusMenu.addChild(new MenuItem({
                    label: 'Activity',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "activity.jsp";});
                    }
                }));
                statusMenu.addChild(new MenuItem({
                    label: 'Usage Statistics',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "statistics.jsp";});
                    }
                }));
                statusMenu.addChild(new MenuItem({
                    label: 'Health',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "health.jsp";});
                    }
                }));
                statusMenu.addChild(new MenuItem({
                    label: 'Active Sessions',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "activesessions.jsp";});
                    }
                }));
                statusMenu.addChild(new MenuItem({
                    label: 'Intruders',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "intruders.jsp";});
                    }
                }));
                topMenuBar.addChild(new PopupMenuBarItem({
                    label: "Current Status",
                    popup: statusMenu
                }));
            }

            { // System Menu
                var systemMenu = new Menu({});
                systemMenu.addChild(new MenuItem({
                    label: 'System Details',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "status.jsp";});
                    }
                }));
                systemMenu.addChild(new MenuItem({
                    label: 'Event Log',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "eventlog.jsp";});
                    }
                }));
                systemMenu.addChild(new MenuItem({
                    label: 'Audit Log',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "auditlog.jsp";});
                    }
                }));
                systemMenu.addChild(new MenuItem({
                    label: 'User Report',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "userreport.jsp";});
                    }
                }));
                systemMenu.addChild(new MenuSeparator());
                systemMenu.addChild(new MenuItem({
                    label: 'Edit Configuration',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "../../private/config/ConfigManager";});
                    }
                }));
                topMenuBar.addChild(new PopupMenuBarItem({
                    label: "System",
                    popup: systemMenu
                }));
            }

            { // Utilities Menu
                var utilityMenu = new Menu({});
                utilityMenu.addChild(new MenuItem({
                    label: 'Token Lookup',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "tokenlookup.jsp";});
                    }
                }));
                topMenuBar.addChild(new PopupMenuBarItem({
                    label: "Utilities",
                    popup: utilityMenu
                }));
            }
            { // Other Menu
                var referenceMenu = new Menu({});
                referenceMenu.addChild(new MenuItem({
                    label: 'REST Services Reference',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = PWM_GLOBAL['url-context'] + "/public/rest.jsp";});
                    }
                }));
                referenceMenu.addChild(new MenuItem({
                    label: 'Software License Reference',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = PWM_GLOBAL['url-context'] + "/public/license.jsp";});
                    }
                }));
                referenceMenu.addChild(new MenuItem({
                    label: 'Error Code Reference',
                    onClick: function() {
                        showWaitDialog(null,null,function(){window.location = "error-reference.jsp";});
                    }
                }));
                if (PWM_GLOBAL['setting-displayEula'] == true) {
                    referenceMenu.addChild(new MenuItem({
                        label: 'View EULA',
                        onClick: function() {
                            showEula(false,null);
                        }
                    }));
                }
                topMenuBar.addChild(new PopupMenuBarItem({
                    label: "Reference",
                    popup: referenceMenu
                }));
            }
            topMenuBar.placeAt("TopMenu");
            topMenuBar.startup();
        });
}
