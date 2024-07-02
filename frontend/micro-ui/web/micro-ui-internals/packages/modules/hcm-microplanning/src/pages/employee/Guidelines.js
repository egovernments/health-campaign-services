import React, { Fragment } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { ArrowForward } from "@egovernments/digit-ui-svg-components";
import { Button } from "@egovernments/digit-ui-react-components";
import { useHistory } from "react-router-dom";
import { ActionBar } from "@egovernments/digit-ui-components";

const Guidelines = ({ path }) => {
  const { t } = useTranslation();
  const history = useHistory()
  // Keeping inline style for now because design for this screen is not given yet
  const { id = "" } = Digit.Hooks.useQueryParams();
  const onNextClick = ()=>{
    history.push(`/${window.contextPath}/employee/microplanning/create-microplan?id=${id}`);
  }
  return (
    <>
      <Link to={`/${window.contextPath}/employee/microplanning/create-microplan?id=${id}`}>
        <div
          style={{
            position: "absolute",
            top: "50%",
            left: "50%",
            transform: "translate(-50%, -50%)",
            padding: "2rem",
            "font-weight": "700",
            "font-size": "2rem",
            color: "rgb(0,0,0)",
          }}
        >
          {t("CREATE_MICROPLAN_GUIDELINES")}
        </div>
      </Link>
      {/* Action bar */}
      <ActionBar className={`guideline-actionbar-content`}>
        {/* Next/Submit button */}
        <Button
          type="button"
          className="custom-button"
          label={t("GUIDELINES_NEXT")}
          onButtonClick={onNextClick}
          isSuffix={true}
          variation={"primary"}
          textStyles={{ padding: 0, margin: 0 }}
        >
          <ArrowForward className={"icon"} width={"1.5rem"} height={"1.5rem"} fill={"rgb(255,255,255)"} />
        </Button>
      </ActionBar>
    </>
  );
};

export default Guidelines;
