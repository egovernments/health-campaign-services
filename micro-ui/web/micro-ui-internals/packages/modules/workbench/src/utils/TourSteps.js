export const TourSteps = {
  '/workbench-ui/employee/workbench/manage-master-data':[
    {
      content:
        'Welcome to Manage Master Data screen. Here you can search and update any master data that is configured for the logged in user tenant',
      target: '.employee-app-wrapper',
      disableBeacon: true,
      placement: 'center',
      title:"Manage Master Data"
    },
    {
      content:
        'Select a module and master name. This will take you to that particular masters search screen where you can add, search, view and update the master data added under that master',
      target: '.manage-master-wrapper',
      disableBeacon: true,
      placement: 'center',
      title:"Manage Master Data"
    },
  ],
  '/workbench-ui/employee/workbench/mdms-search-v2':[
    {
      content:
        'Welcome to the master data search screen. Here you can search the master data added under this master',
      target: '.action-bar-wrap',
      disableBeacon: true,
      placement: 'bottom',
      title:"Manage Master Data"
    },
    {
      content:
        'To add new master data under this master click on the Add Master Data button',
      target: '.action-bar-wrap',
      disableBeacon: true,
      placement: 'auto',
      title:"Manage Master Data"
    },
    
  ]
}
