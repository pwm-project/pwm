/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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


import { element, forEach, IAttributes, IAugmentedJQuery, IScope } from 'angular';

interface ITabsetController {
    activeTab: number;
    activateTab(tabIndex: number): void;
}

export class TabsetController implements ITabsetController {
    activeTab;

    activateTab(tabIndex): void {
        this.activeTab = tabIndex;
    }
}

require('ux/tabset.directive.scss');

export default function TabsetDirective() {
    return {
        scope: true,
        restrict: 'E',
        controller: TabsetController,
        controllerAs: '$tabsetCtrl',
        compile: (tElement: IAugmentedJQuery) => {
            // Nest element contents inside a tabset
            let tabset = element(`<div class="mf-tabset" role="tablist"></div>`);
            forEach(tElement.contents(), (content: Element) => {
                tabset.append(content);
            });
            tElement.append(tabset);

            // Switch out 'mf-tab' elements for tabs and panes
            let tabs = element(tabset).find('mf-tab');
            let tab;
            forEach(tabs, (tabElement: HTMLElement, index: number) => {
                // Add tab
                let label = tabElement.getAttribute('label');
                tab = element(`<button ng-class="{'mf-tab': true, 'mf-selected': $tabsetCtrl.activeTab === ${index}}"
                                       ng-click="$tabsetCtrl.activateTab(${index})"
                                       role="tab">${label}</button>`);
                element(tabElement).replaceWith(tab);

                // Add pane
                let pane = element(`<div class="mf-tab-pane-container"
                                         ng-if="$tabsetCtrl.activeTab === ${index}"></div>`);
                pane.append(tabElement.innerHTML);
                tElement.append(pane);
            });

            // Add tab base and fill after the last tab, before any right-aligned elements such as links
            if (tab) {
                tab.after(`<div class="mf-tab-base"></div><div class="mf-fill"></div>`);
            }

            return (scope: IScope, iElement: IAugmentedJQuery, iAttrs: IAttributes, tabsetCtrl: ITabsetController) => {
                tabsetCtrl.activeTab = Number(iAttrs.mfActiveTab) || 0;
            };
        }
    };
}
