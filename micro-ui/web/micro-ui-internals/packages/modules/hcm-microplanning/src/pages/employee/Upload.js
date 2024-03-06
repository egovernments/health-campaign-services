import React, { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
// import { Config } from "../../configs/UploadConfig";
import * as Icons from "@egovernments/digit-ui-react-components";

const Upload = () => {
    const { t } = useTranslation();

    // Fetching data using custom MDMS hook
    const { isLoading: idk, data } = Digit.Hooks.useCustomMDMS(Digit.ULBService.getCurrentTenantId(), "hcm-microplanning", [{ name: "UploadSections" }]);
    
    // State to store sections and selected section
    const [sections, setSections] = useState([]);
    const [selectedSection, setSelectedSection] = useState(null);
    
    // Effect to update sections and selected section when data changes
    useEffect(() => {
        if (data) {
            const uploadSections = data["hcm-microplanning"]["UploadSections"];
            setSections(uploadSections);
            setSelectedSection(uploadSections.length > 0 ? uploadSections[0].id : null);
        }
    }, [data]);

    // Memoized section options to prevent unnecessary re-renders
    const sectionOptions = useMemo(() =>
        sections.map((item) => (
            <UploadSection
                key={item.id}
                item={item}
                selected={selectedSection === item.id}
                setSelectedSection={setSelectedSection}
            />
        )), [sections, selectedSection]);

    // Memoized section components to prevent unnecessary re-renders
    const sectionComponents = useMemo(() =>
        sections.map((item) => (
            <div key={item.id} className={`upload-section ${selectedSection === item.id ? "upload-section-active" : "upload-section-inactive"}`}>
                <p>{item.title}</p>
            </div>
        )), [sections, selectedSection]);

    return (
        <div className="jk-header-btn-wrapper microplanning">
            <div className="upload">
                <div>
                    {sectionComponents}
                </div>
                <div className="upload-section-option">
                    {sectionOptions}
                </div>
            </div>
        </div>
    );
};

// Component for rendering individual section option
const UploadSection = ({ item, selected, setSelectedSection }) => {
    // Custom icon component
    const CustomIcon = ({ Icon, color }) => {
        if (!Icon) return null;
        return <Icon style={{ fill: color }} />;
    };

    // Handle click on section option
    const handleClick = () => {
        setSelectedSection(item.id);
    };

    return (
        <div
            className={`upload-section-options ${selected ? "upload-section-options-active" : "upload-section-options-inactive"}`}
            onClick={handleClick}
        >
            <div style={{ padding: "0 10px" }}>
                <CustomIcon Icon={Icons[item.iconName]} color={selected ? "rgba(244, 119, 56, 1)" : "rgba(214, 213, 212, 1)"} />
            </div>
            <p>{item.title}</p>
            <div style={{ marginLeft: "auto", marginRight: 0 }}>
                <CustomIcon Icon={Icons["TickMarkBackgroundFilled"]} color={"rgba(255, 255, 255, 0)"} />
            </div>
        </div>
    );
};

export default Upload;
