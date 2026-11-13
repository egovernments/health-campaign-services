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

/** One row of a generated sheet: column key -> cell value. */
interface SheetRow {
    [columnName: string]: string | number;
}

interface SheetMap {
    [sheetName: string]: {
        dynamicColumns: { [columnName: string]: ColumnProperties } | null;
        data: SheetRow[];
    };
}

export { ColumnProperties, SheetMap, SheetRow };
