import React from "react";
import { WareHouses } from "./WareHouses";

export default {
  tags: ['autodocs'],
  argTypes: {
    className: {
        options: ['custom-class'],
        control: { type: 'check' },
    }
  },
  title: "WareHouses",
  component: WareHouses,
};

export const Default = () => <WareHouses />;
export const Fill = () => <WareHouses fill="blue" />;
export const Size = () => <WareHouses height="50" width="50" />;
export const CustomStyle = () => <WareHouses style={{ border: "1px solid red" }} />;
export const CustomClassName = () => <WareHouses className="custom-class" />;

export const Clickable = () => <WareHouses onClick={()=>console.log("clicked")} />;

const Template = (args) => <WareHouses {...args} />;

export const Playground = Template.bind({});
Playground.args = {
  className: "custom-class",
  style: { border: "3px solid green" }
};
