import { Component } from '../component';

@Component({
    stylesheetUrl: require('ux/app-bar.component.scss'),
    template: `<div class="app-bar-content" ng-transclude></div>`,
    transclude: true
})
export default class AppBarComponent {
}
