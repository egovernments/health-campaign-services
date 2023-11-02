# Underlying library used: 

react-joyride

## React Tutorial Component

This is a set of React components for creating interactive tutorials within your React application.
There are 3 things that are exported from react components library:
1. TourProvider -> This is a HOC which maintains a globalState for tour and provides it to all child components. Wrap your app with this component. A useTourState hook is also exported from this component which can be imported in any child component to access and update the globalState.
2. Help -> This is basically a dummy component for help icon which accepts a callback called startTour as prop. StartTour is fired on onClick of Help icon to start the tour. 
3. Tutorial -> This is the main component which makes use of the globalState to show the tutorial. 

### Usage

1. Wrap your app component with TourProvider
2. Render Tutorial somewhere inside the app
3. Render Help component
4. On onClick of help update the TourState. You need to pass an object similar to the following in globalState
    {
      run: boolean,
      steps: Array of steps,
      tourActive: dummy state maintained to add custom logic like re-direction,
    }
Array of steps looks like this:
[
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

#### References:

Refer the documentation of joyride for more information: https://docs.react-joyride.com/
Refer the following commit for an exemplar done in workbench-ui: https://github.com/egovernments/DIGIT-Frontend/commit/0ab01509fc93dd94065146d9c218fd0e59310936
```bash

