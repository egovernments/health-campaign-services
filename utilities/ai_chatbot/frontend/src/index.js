import React from "react";
import reportWebVitals from "./reportWebVitals";
import ReactDOM from "react-dom/client";
import { AuthContextProvider } from "./state/authContex";
import { MathJaxContext } from "better-react-mathjax";
import { ClickedTextProvider } from "./components/ClickedText/clickedtext";
import "./index.scss";
import LayoutView from "./pages/Layout";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <React.StrictMode>
    <MathJaxContext>
      <AuthContextProvider>
        <ClickedTextProvider>
          <LayoutView />
        </ClickedTextProvider>
      </AuthContextProvider>
    </MathJaxContext>
  </React.StrictMode>
);

reportWebVitals();
