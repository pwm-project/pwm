import { module } from 'angular';
import AppBarComponent from './app-bar.component';
import AutoCompleteComponent from './auto-complete.component';
import IconButtonComponent from './icon-button.component';
import TableComponent from './table.component';

var moduleName = 'peoplesearch.ux';

module(moduleName, [ ])
    .component('mfAppBar', AppBarComponent)
    .component('mfAutoComplete', AutoCompleteComponent)
    .component('mfIconButton', IconButtonComponent)
    .component('mfTable', TableComponent);

export default moduleName;
