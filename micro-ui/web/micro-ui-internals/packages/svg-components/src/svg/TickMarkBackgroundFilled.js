import React from "react";
import PropTypes from "prop-types";

export const TickMarkBackgroundFilled = ({ className, width = "40", height = "46", style = {}, fill = "#F47738", onClick = null }) => {
  return (
    <svg width={width} height={height} className={className} onClick={onClick} style={style} viewBox="0 0 40 46" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        fill-rule="evenodd"
        clip-rule="evenodd"
        d="M15.9998 2.66797C8.63984 2.66797 2.6665 8.6413 2.6665 16.0013C2.6665 23.3613 8.63984 29.3346 15.9998 29.3346C23.3598 29.3346 29.3332 23.3613 29.3332 16.0013C29.3332 8.6413 23.3598 2.66797 15.9998 2.66797ZM13.3332 22.668L6.6665 16.0013L8.5465 14.1213L13.3332 18.8946L23.4532 8.77464L25.3332 10.668L13.3332 22.668Z"
        fill={fill}
      />
    </svg>
  );
};



TickMarkBackgroundFilled.propTypes = {
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
