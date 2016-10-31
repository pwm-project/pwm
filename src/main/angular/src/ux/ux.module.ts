import { module } from 'angular';
import AppBarComponent from './app-bar.component';
import IconButtonComponent from './icon-button.component';

var moduleName = 'peoplesearch.ux';

module(moduleName, [ ])
    .component('appBar', AppBarComponent)
    .component('iconButton', IconButtonComponent);

export default moduleName;
