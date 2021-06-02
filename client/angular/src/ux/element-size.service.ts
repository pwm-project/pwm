/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
