import React from "react";
import PropTypes from "prop-types";
import classNames from "classnames";
import { ThreeCircles, Oval, Bars, RotatingSquare } from "react-loader-spinner";

const SIZE_MAP = {
  small: { width: "40px", height: "40px" },
  medium: { width: "60px", height: "60px" },
  large: { width: "100px", height: "100px" },
};

const SPINNER_TYPES = {
  Oval,
  Bars,
  RotatingSquare,
  ThreeCircles,
};

function LoadingWidget({
  type = "ThreeCircles",
  size = "large",
  height = "100%",
  width = "100%",
  color = "rgb(42, 113, 188)",
  className = "",
  ...props
}) {
  const Loader = SPINNER_TYPES[type] || ThreeCircles;
  const { width: spinnerWidth, height: spinnerHeight } = SIZE_MAP[size] || SIZE_MAP.large;

  return (
    <div
      className={classNames(
        "flex items-center justify-center",
        className,
        {
          "h-screen": height === "screen",
          "h-full": height === "100%",
          "w-screen": width === "screen",
          "w-full": width === "100%",
        }
      )}
      style={{ height: height === "100%" ? "60vh" : undefined }}
    >
      <Loader
        color={color}
        height={spinnerHeight}
        width={spinnerWidth}
        ariaLabel="loading-spinner"
        {...props}
      />
    </div>
  );
}

LoadingWidget.propTypes = {
  type: PropTypes.oneOf(["Oval", "Bars", "ThreeCircles", "RotatingSquare"]),
  size: PropTypes.oneOf(["small", "medium", "large"]),
  height: PropTypes.string,
  width: PropTypes.string,
  color: PropTypes.string,
  className: PropTypes.string,
};

LoadingWidget.defaultProps = {
  size: "large", 
};

export default React.memo(LoadingWidget);
