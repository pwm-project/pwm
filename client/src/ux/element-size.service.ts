/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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


import { element, IAugmentedJQuery, IRootScopeService, IWindowService } from 'angular';

interface IResizeCallback {
    (newValue: number, oldValue: number): void;
}

function dasherize(input: string): string {
    return input
        .replace(/(?:^\w|[A-Z]|\b\w)/g, function (letter, index) {
            return (index == 0 ? '' : '-') + letter.toLowerCase();
        })
        .replace(/\s+/g, '');
}

class ElementSizeWatcher<T> {
    callbacks: IResizeCallback[] = [];
    sizes: any[] = [];
    width: number;

    constructor(public element: IAugmentedJQuery, widths: T) {
        // Build size information
        for (let width in widths) {
            if (widths.hasOwnProperty(width) && !/^\d+$/.test(width)) {
                this.sizes.push({
                    size: widths[width],
                    className: dasherize(width),
                    type: width
                });
            }
        }

        this.sizes.sort((size1: any, size2: any) => size1.size - size2.size);

        this.updateWidth();
    }

    get elementWidth(): number {
        return this.element[0].clientWidth;
    }

    onResize(callback: IResizeCallback) {
        this.callbacks.push(callback);

        callback(this.elementWidth, this.width);
    }

    updateWidth(): void {
        if (this.width !== this.elementWidth) {
            this.callbacks.forEach(callback => callback(this.elementWidth, this.width));
            this.width = this.elementWidth;
            this.updateElementClass();
        }
    }

    private updateElementClass(): void {
        // Remove all size classes
        this.sizes.forEach((size) => {
            this.element.removeClass(size.className);
        });

        // Add applicable sizes
        let applicableClasses = this.sizes
            .filter((size: any) => this.width >= size.size)
            .map((size: any) => size.className)
            .join(' ');
        this.element.addClass(applicableClasses);
    }
}

export default class ElementSizeService {
    private elementSizeWatchers: ElementSizeWatcher<any>[] = [];

    static $inject = ['$rootScope', '$window'];
    constructor(private $rootScope: IRootScopeService, private $window: IWindowService) {

    }

    watchWidth<T>(el: IAugmentedJQuery, widths: T): ElementSizeWatcher<T> {
        if (!this.elementSizeWatchers.length) {
            element(this.$window).on('resize', this.onWindowResize.bind(this));
        }

        return this.addElementSizeWatcher<T>(el, widths);
    }

    private addElementSizeWatcher<T>(element: IAugmentedJQuery, widths: T): ElementSizeWatcher<T> {
        let elementSizeWatcher = new ElementSizeWatcher<T>(element, widths);
        // TODO: check if element already exists
        this.elementSizeWatchers.push(elementSizeWatcher);
        return elementSizeWatcher;
    }

    private onWindowResize() {
        // TODO: optimizations
        // TODO: height (later)
        this.elementSizeWatchers.forEach((watcher: ElementSizeWatcher<any>) => {
            watcher.updateWidth();
        });

        this.$rootScope.$apply();
    }
}
