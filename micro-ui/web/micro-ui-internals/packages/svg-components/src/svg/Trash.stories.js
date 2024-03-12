import React from "react";
import { Trash } from "./Trash";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "Trash",
  component: Trash,
};

export const Default = () => <Trash />;
export const Fill = () => <Trash fill="blue" />;
export const Size = () => <Trash height="50" width="50" />;
export const CustomStyle = () => <Trash style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <Trash className="custom-class" />;

export const Clickable = () => <Trash onClick={()=>console.log("clicked")} />;

const Template = (args) => <Trash {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
