import { getReadMeConfig } from "../utils/genericUtils";
import { SheetMap } from "../models/SheetMap";
import { getLocalizedName } from "../utils/campaignUtils";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        console.log("Generating template with config:", templateConfig);
        console.log("Response to send:", responseToSend);

        const readMeConfig = await getReadMeConfig(responseToSend.tenantId, responseToSend.type);
        
        const readMeColumnHeader = getLocalizedName(Object.keys(templateConfig?.sheets?.[0]?.schema?.properties)?.[0], localizationMap);

        const readMeData : any= this.getReadMeData(readMeConfig, readMeColumnHeader, localizationMap);

        const sheetMap: SheetMap = {
            [getLocalizedName(templateConfig?.sheets?.[0]?.sheetName, localizationMap)]: {
                data : readMeData,
                dynamicColumns: {
                    [readMeColumnHeader]: {
                        adjustHeight: true,
                        width: 120
                    }
                }
            }
        }; // Initialize the SheetMap object

        return sheetMap;
    }

    static getReadMeData(readMeConfig: any, readMeColumnHeader: any, localizationMap: any) {
        const dataArray = [];
        for(const text of readMeConfig?.texts) {
           dataArray.push({[readMeColumnHeader]: ""});
           dataArray.push({ [readMeColumnHeader]: "" });
           let header = getLocalizedName(text.header, localizationMap);
           if(text.isHeaderBold) {
               header = `**${header}**`;
           }
           dataArray.push({
               [readMeColumnHeader]: header
           })
           for(const description of text.descriptions) {
               dataArray.push({
                   [readMeColumnHeader]: getLocalizedName(description.text, localizationMap)
               })
           }
        }
        return dataArray;
    }
}
