import React from "react";
import PropTypes from "prop-types";

export const WareHouses = ({ className, width = "40", height = "46", style = {}, fill = "#F47738", onClick = null }) => {
  return (
    <svg width={width} height={height} className={className} onClick={onClick} style={style} viewBox="0 0 40 46" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        fill-rule="evenodd"
        clip-rule="evenodd"
        d="M25 5.83203H5V8.7487H25V5.83203ZM26.25 20.4154V17.4987L25 10.207H5L3.75 17.4987V20.4154H5V29.1654H17.5V20.4154H22.5V29.1654H25V20.4154H26.25ZM15 26.2487H7.5V20.4154H15V26.2487Z"
        fill={fill}
      />
    </svg>
  );
};



WareHouses.propTypes = {
  /** custom width of the svg icon */
  width: PropTypes.string,
  /** custom height of the svg icon */
  height: PropTypes.string,
  /** custom colour of the svg icon */
  fill: PropTypes.string,
  /** custom class of the svg icon */
  className: PropTypes.string,
  /** custom style of the svg icon */
  style: PropTypes.object,
  /** Click Event handler when icon is clicked */
  onClick: PropTypes.func,
};
