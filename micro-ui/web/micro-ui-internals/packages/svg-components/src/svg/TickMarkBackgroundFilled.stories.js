import React from "react";
import { TickMarkBackgroundFilled } from "./TickMarkBackgroundFilled";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "TickMarkBackgroundFilled",
  component: TickMarkBackgroundFilled,
};

export const Default = () => <TickMarkBackgroundFilled />;
export const Fill = () => <TickMarkBackgroundFilled fill="blue" />;
export const Size = () => <TickMarkBackgroundFilled height="50" width="50" />;
export const CustomStyle = () => <TickMarkBackgroundFilled style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <TickMarkBackgroundFilled className="custom-class" />;

export const Clickable = () => <TickMarkBackgroundFilled onClick={()=>console.log("clicked")} />;

const Template = (args) => <TickMarkBackgroundFilled {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
