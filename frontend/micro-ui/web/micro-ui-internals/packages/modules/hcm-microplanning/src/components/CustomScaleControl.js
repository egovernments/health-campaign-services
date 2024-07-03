import React, { useEffect, useState } from "react";

const CustomScaleControl = ({ map }) => {
  if (!map) return null;
  const [scaleText, setScaleText] = useState("");
  // Function to calculate and update the scale text
  const updateScale = () => {
    // Calculate the scale based on the map's current zoom level
    const maxWidthMeters = map.containerPointToLatLng([0, map.getSize().y]).distanceTo(map.containerPointToLatLng([100, map.getSize().y]));
    const scale = maxWidthMeters / 1000; // Convert to kilometers

    // Format the scale text
    const scaleTextData = scale < 1 ? `${Math.round(scale * 1000)} m` : `${Math.round(Math.round(scale.toFixed(0) / 10) * 10)} km`;

    // Update the scale text in the container element
    setScaleText(scaleTextData);
  };

  // Effect to update the scale text when the map component mounts and on map zoom change
  useEffect(() => {
    // Update the scale text initially
    updateScale();

    // Register the map's zoom events to update the scale text
    map.on("zoomend", updateScale);

    // Clean up event listener when the component unmounts
    return () => {
      map.off("zoomend", updateScale);
    };
  }, [map]);

  return (
    <div className="custom-scale" aria-live="polite">
      {scaleText}
      <div className="border-spikes" aria-hidden="true" />
    </div>
  );
};

export default CustomScaleControl;
