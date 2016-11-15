import { module } from 'angular';
import AppBarComponent from './app-bar.component';
import AutoCompleteComponent from './auto-complete.component';
import ButtonComponent from './button.component';
import DialogComponent from './dialog.component';
// import { DialogService } from './dialog.service';
import IconButtonComponent from './icon-button.component';
import IconComponent from './icon.component';
import SearchBarComponent from './search-bar.component';
import TableDirectiveFactory from './table.directive';
import TableColumnDirectiveFactory from './table-column.directive';

var moduleName = 'peoplesearch.ux';

module(moduleName, [ ])
    .component('mfAppBar', AppBarComponent)
    .component('mfAutoComplete', AutoCompleteComponent)
    .component('mfButton', ButtonComponent)
    .component('mfDialog', DialogComponent)
    .component('mfIconButton', IconButtonComponent)
    .component('mfIcon', IconComponent)
    .component('mfSearchBar', SearchBarComponent)
    .directive('mfTable', TableDirectiveFactory)
    .directive('mfTableColumn', TableColumnDirectiveFactory);
    // .service('MfDialogService', DialogService);

export default moduleName;
