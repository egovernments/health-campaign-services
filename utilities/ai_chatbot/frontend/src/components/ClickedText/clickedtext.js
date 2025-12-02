// ClickedTextContext.js
import React, { createContext, useContext, useState } from 'react';

const ClickedTextContext = createContext();

export const useClickedText = () => useContext(ClickedTextContext);

export const ClickedTextProvider = ({ children }) => {
  const [clickedText, setClickedText] = useState("");

  return (
    <ClickedTextContext.Provider value={{ clickedText, setClickedText }}>
      {children}
    </ClickedTextContext.Provider>
  );
};
