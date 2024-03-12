import React from "react";
import { SpatialDocument } from "./SpatialDocument";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "SpatialDocument",
  component: SpatialDocument,
};

export const Default = () => <SpatialDocument />;
export const Fill = () => <SpatialDocument fill="blue" />;
export const Size = () => <SpatialDocument height="50" width="50" />;
export const CustomStyle = () => <SpatialDocument style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <SpatialDocument className="custom-class" />;

export const Clickable = () => <SpatialDocument onClick={()=>console.log("clicked")} />;

const Template = (args) => <SpatialDocument {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
