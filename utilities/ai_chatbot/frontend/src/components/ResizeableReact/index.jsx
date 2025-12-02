import "./styles.scss";
import React from 'react';
import 'react-resizable/css/styles.css';
import { Resizable } from 'react-resizable';

/**
 * ResizableReact Component
 * 
 * This component is used to create a resizable table column header.
 * It utilizes the 'react-resizable' library to allow users to adjust column widths dynamically.
 *
 */
const ResizableReact = (props) => {
  const { onResize, width, ...restProps } = props;

  // If no width is provided, render a standard table header
  if (!width) {
    return <th {...restProps} />;
  }

  return (
    <Resizable
      width={width}
      height={0} // Height is set to 0 as we are only resizing the width
      onResize={onResize}
      resizeHandles={['e']} // Enables resizing from the right edge
      draggableOpts={{ enableUserSelectHack: false }} // Prevents text selection issues while dragging
    >
      <th {...restProps} />
    </Resizable>
  );
};

export default ResizableReact;
