import { Component } from '../component';

@Component({
    stylesheetUrl: require('ux/button.component.scss'),
    template: '<ng-transclude></ng-transclude>',
    transclude: true
})
export default class ButtonComponent {
}
