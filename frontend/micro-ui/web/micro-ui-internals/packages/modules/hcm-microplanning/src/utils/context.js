import React,{useContext,createContext,useReducer} from "react"

const MyContext = createContext()
const initialState = {
  
}

const reducer = (state=initialState,action) => {
  switch (action.type) {
    case "SETINITDATA":
      return {...state,...action.state}
    default:
     return state;
  }
}

export const useMyContext = () => {

  return useContext(MyContext)
}

export const ProviderContext = ({children}) => {

  const [state,dispatch] = useReducer(reducer,initialState)

  return (
    <MyContext.Provider value={{state,dispatch}}>
      {children}
    </MyContext.Provider>
  )
}