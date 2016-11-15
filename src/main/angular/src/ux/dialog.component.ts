import { Component } from '../component';
import { IDocumentService } from 'angular';

@Component({
    bindings: {
        onClose: '&'
    },
    stylesheetUrl: require('ux/dialog.component.scss'),
    templateUrl: require('ux/dialog.component.html'),
    transclude: true
})
export default class DialogComponent {
    onClose: (() => void);
    onKeyDown: ((event) => void);

    static $inject = [ '$document' ];
    constructor(private $document: IDocumentService) {
        var self = this;

        this.onKeyDown = (event) => {
            if (event.keyCode === 27) { // ESC
                self.closeDialog();
            }

            event.stopImmediatePropagation();
        };
    }

    $onInit(): void {
        this.$document.on('keydown', this.onKeyDown);
    }

    $onDestroy(): void {
        this.$document.off('keydown', this.onKeyDown);
    }

    closeDialog(): void {
        this.onClose();
    }
}
