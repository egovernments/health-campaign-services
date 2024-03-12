import React from "react";
import { Facility } from "./Facility";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "Facility",
  component: Facility,
};

export const Default = () => <Facility />;
export const Fill = () => <Facility fill="blue" />;
export const Size = () => <Facility height="50" width="50" />;
export const CustomStyle = () => <Facility style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <Facility className="custom-class" />;

export const Clickable = () => <Facility onClick={()=>console.log("clicked")} />;

const Template = (args) => <Facility {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
