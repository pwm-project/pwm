import { Component } from '../component';

@Component({
    bindings: {
        icon: '@'
    },
    stylesheetUrl: require('ux/icon-button.component.scss'),
    template: `<img ng-src="{{$ctrl.getIconSrc()}}" />`
})
export default class IconButtonComponent {
    icon: string;

    getIconSrc() {
        if (this.icon) {
            return `/images/m_${this.icon}.svg`;
        }

        return null;
    }
}
