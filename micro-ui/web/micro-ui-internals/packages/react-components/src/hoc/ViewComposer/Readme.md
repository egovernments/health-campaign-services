# Intro: 

ViewComposer is basically a replacement of ApplicationDetails component which was a generic component for View Screens. That got very cluttered with custom logic. So to keep the business logic abstract and generic we made this component.

## High level steps to use:
1. Simply import it from react-components library
2. It expects two things as props -> data,isLoading
3. data contains all the details to show in the view screen with specific format, basically we'll be returning this data object from the hook call.

### Format of data expected by ViewComposer
Below is an example object:

<!-- {
  cards:[
    {
      sections: [
        {
          type: "DATA",
          sectionHeader: { value: "Section 1", inlineStyles: {} },
          cardHeader: { value: "Card 2", inlineStyles: {} },
          values: [
            {
              key: "key 1",
              value: "value 1",
            },
            {
              key: "key 2",
              value: "value 2",
            },
            {
              key: "key 3",
              value: "value 3",
            },
          ],
        },
        {
          type: "DATA",
          sectionHeader: { value: "Section 2", inlineStyles: { marginTop: "2rem" } },
          // cardHeader:{value:"Card 1",inlineStyles:{}},
          values: [
            {
              key: "key 1",
              value: "value 1",
            },
            {
              key: "key 2",
              value: "value 2",
            },
            {
              key: "key 3",
              value: "value 3",
            },
          ],
        },
        {
          type: "DOCUMENTS",
          documents: [
            {
              title: "WORKS_RELEVANT_DOCUMENTS",
              BS: "Works",
              values: [
                {
                  title: "Proposal document",
                  documentType: "PROJECT_PROPOSAL",
                  documentUid: "cfed582b-31b0-42e9-985f-fb9bb4543670",
                  fileStoreId: "cfed582b-31b0-42e9-985f-fb9bb4543670",
                },
                {
                  title: "Finalised worklist",
                  documentType: "FINALIZED_WORKLIST",
                  documentUid: "f7543894-d3a1-4263-acb2-58b1383eebec",
                  fileStoreId: "f7543894-d3a1-4263-acb2-58b1383eebec",
                },
                {
                  title: "Feasibility analysis",
                  documentType: "FEASIBILITY_ANALYSIS",
                  documentUid: "c4fb4f5d-a4c3-472e-8991-e05bc2d671f5",
                  fileStoreId: "c4fb4f5d-a4c3-472e-8991-e05bc2d671f5",
                },
              ],
            },
          ],
          inlineStyles: {
            marginTop: "1rem",
          },
        },
        {
          type: "WFHISTORY",
          businessService: "ESTIMATE",
          applicationNo: "ES/2023-24/000828",
          tenantId: "pg.citya",
          timelineStatusPrefix: "TEST",
        },
        {
          type: "WFACTIONS",
          forcedActionPrefix: "TEST",
          businessService: "ESTIMATE",
          applicationNo: "ES/2023-24/000828",
          tenantId: "pg.citya",
          applicationDetails: {},
          url: "/estimate/v1/_update",
          moduleCode: "Estimate",
          editApplicationNumber: undefined,
        },
      ],
    },
  ],
  apiResponse:{},
  additionalDetails:{}
} -->


4. Basically cards is an array of objects each representing a Card in the View Screen.
5. Each card can have multiple sections with the following types defined: [DATA,DOCUMENTS,WFHISTORY,WFACTIONS,COMPONENT]
6. We can render content based on these types defined.


#### Final Summary
1. Import ViewComposer
2. Write hook call that returns data expected by the ViewComposer
3. Pass in the data and isLoading props and Viola!





