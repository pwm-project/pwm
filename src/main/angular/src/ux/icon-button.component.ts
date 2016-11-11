import { Component } from '../component';

@Component({
    bindings: {
        icon: '@'
    },
    stylesheetUrl: require('ux/icon-button.component.scss'),
    template: `<mf-icon icon="{{$ctrl.icon}}"></mf-icon>`
})
export default class IconButtonComponent {}
