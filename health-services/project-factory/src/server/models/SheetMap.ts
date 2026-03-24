interface ColumnProperties {
    width?: number;
    color?: string;
    orderNumber?: number;
    freezeColumn?: boolean;
    hideColumn?: boolean;
    adjustHeight?: boolean;
    unFreezeColumnTillData?: boolean;
    freezeColumnIfFilled?: boolean;
    showInProcessed?: boolean;
    freezeTillData?: boolean;
    wrapText?: boolean;
}

interface SheetMap {
    [sheetName: string]: {
        dynamicColumns: { [columnName: string]: ColumnProperties } | null;
        data: { [columnName: string]: string | number }[];
    };
}

export { ColumnProperties, SheetMap };
