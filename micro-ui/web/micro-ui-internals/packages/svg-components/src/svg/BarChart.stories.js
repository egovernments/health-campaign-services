import React from "react";
import { BarChart } from "./BarChart";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "BarChart",
  component: BarChart,
};

export const Default = () => <BarChart />;
export const Fill = () => <BarChart fill="blue" />;
export const Size = () => <BarChart height="50" width="50" />;
export const CustomStyle = () => <BarChart style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <BarChart className="custom-class" />;

export const Clickable = () => <BarChart onClick={()=>console.log("clicked")} />;

const Template = (args) => <BarChart {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
