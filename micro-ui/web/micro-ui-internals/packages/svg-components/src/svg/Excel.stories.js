import React from "react";
import { Excel } from "./Excel";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "Excel",
  component: Excel,
};

export const Default = () => <Excel />;
export const Fill = () => <Excel fill="blue" />;
export const Size = () => <Excel height="50" width="50" />;
export const CustomStyle = () => <Excel style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <Excel className="custom-class" />;

export const Clickable = () => <Excel onClick={()=>console.log("clicked")} />;

const Template = (args) => <Excel {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
