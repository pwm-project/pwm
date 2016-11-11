import { Component } from '../component';

@Component({
    bindings: {
        icon: '@'
    },
    stylesheetUrl: require('ux/icon.component.scss'),
    template: `<i class="icon" ng-class="$ctrl.getIconClass()" />`
})
export default class IconComponent {
    icon: string;

    getIconClass() {
        if (this.icon) {
            return `icon_m_${this.icon}`;
        }

        return null;
    }
}
