export function DasherizeFilter(): (input: string) => string {
    return (input: string): string => {
        return input
            .replace(/(?:^\w|[A-Z]|\b\w)/g, function (letter, index) {
                return (index == 0 ? '' : '-') + letter.toLowerCase();
            })
            .replace(/\s+/g, '');
    };
}
