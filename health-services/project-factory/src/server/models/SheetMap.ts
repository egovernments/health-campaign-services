interface ColumnProperties {
    width?: number;
    color?: string;
    orderNumber?: number;
    freezeColumn?: boolean;
    unfreezeTillData?: boolean;
    hideColumn?: boolean;
    adjustHeight?: boolean;
    unFreezeColumnTillData?: boolean;
    showInProcessed?: boolean;
}

interface SheetMap {
    [sheetName: string]: {
        dynamicColumns: { [columnName: string]: ColumnProperties } | null;
        data: { [columnName: string]: string | number }[];
    };
}

export { ColumnProperties, SheetMap };
