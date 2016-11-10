export default class Column {
    constructor(public label: string,
                public valueExpression: string,
                public visible?: boolean) {
        this.visible = visible !== false;
    }
}
