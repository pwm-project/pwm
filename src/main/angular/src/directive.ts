import * as angular from 'angular';

export default function Directive(options: {
    bindToController?: boolean,
    controller?: (string | any),
    controllerAs?: string,
    restrict?: string,
    template?: string,
    templateUrl?: string,
    transclude?: boolean,
    scope?: (boolean | any),
    stylesheetUrl?: string
}) {
    // Consistent default behavior for components and directives
    if (options.controller) {
        options.controllerAs = options.controllerAs || '$ctrl';

        // If bindToController is any value that is not false, set it to true
        options.bindToController = options.bindToController != false;
    }

    return (directive: Function) => angular.extend(options, directive);
}
