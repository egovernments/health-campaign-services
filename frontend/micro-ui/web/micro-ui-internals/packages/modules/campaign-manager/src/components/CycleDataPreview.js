import { Card, LabelFieldPair, Row } from "@egovernments/digit-ui-react-components";
import React, { Fragment, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import DetailsTable from "./DetailsTable";
import { Button, InfoCard } from "@egovernments/digit-ui-components";

const Tabs = ({ deliveryData, onTabChange }) => {
  // const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const { t } = useTranslation();

  return (
    <div style={{ marginTop: "1.5rem", marginBottom: "1.5rem", display: "flex" }}>
      {deliveryData?.map((_, index) => (
        <button
          key={index}
          type="button"
          className={`campaign-sub-tab-head ${_.active === true ? "active" : ""} hover`}
          onClick={() => onTabChange(_.deliveryIndex, index)}
        >
          {t(`CAMPAIGN_DELIVERY`)} {index + 1}
        </button>
      ))}
    </div>
  );
};

const CycleDataPreview = ({ data, items, index, errors, onErrorClick, cardErrors }) => {
  const { t } = useTranslation();
  const [deliveryData, setDeliveryData] = useState(data?.deliveries);
  const [activeTab, setActiveTab] = useState(1);

  useEffect(() => {
    setDeliveryData(data?.deliveries);
  }, [data?.deliveries]);

  const handleTabChange = (tabIndex, index) => {
    setDeliveryData((prev) => {
      return prev.map((i) => {
        if (i.deliveryIndex == tabIndex) {
          return {
            ...i,
            active: true,
          };
        } else {
          return {
            ...i,
            active: false,
          };
        }
      });
    });
  };
  // return null;
  return (
    <>
      {cardErrors?.map((i) => (
        <InfoCard
          populators={{
            name: "infocard",
          }}
          variant="error"
          text={t(i?.error ? i?.error : i?.message)}
          hasAdditionalElements={true}
          additionalElements={[<Button className={"error"} label={i?.button} onClick={i.onClick} />]}
        />
      ))}
      {/* {i.error ? i.error : i.message)}</div> */}
      <div className="employee-data-table ">
        {data?.startDate && (
          <Row
            key={t("startDate")}
            label={`${t("Start Date")}`}
            text={data?.startDate}
            className="border-none"
            rowContainerStyle={{ display: "flex" }}
            labelStyle={{ fontWeight: "500" }}
          />
        )}
        {data?.endDate && (
          <Row
            key={t("endDate")}
            label={`${t("End Date")}`}
            text={data?.endDate}
            className="border-none"
            rowContainerStyle={{ display: "flex" }}
            labelStyle={{ fontWeight: "500" }}
          />
        )}
      </div>

      <hr style={{ border: "1px solid #d6d5d4" }} />

      <Tabs deliveryData={deliveryData} tabCount={deliveryData?.length} activeTab={activeTab} onTabChange={handleTabChange} />

      {deliveryData
        .find((i) => i.active === true)
        ?.deliveryRules?.map((rules, ruleIndex) => {
          return (
            <Card className="delivery-preview-card">
              {rules?.attributes?.length > 0 && (
                <DetailsTable
                  className="campaign-attribute-table"
                  cardHeader={{ value: `Condition ${ruleIndex + 1}` }}
                  columnsData={[
                    {
                      Header: t("Attribute"),
                      accessor: "attribute",
                    },
                    {
                      Header: t("Operator"),
                      accessor: "operator",
                    },
                    {
                      Header: t("Value"),
                      accessor: "value",
                    },
                  ]}
                  rowsData={rules?.attributes}
                />
              )}
              {rules?.products?.length > 0 && (
                <DetailsTable
                  className="campaign-product-table"
                  // cardHeader={{ value: "Product Details" }}
                  columnsData={[
                    {
                      Header: t("Product"),
                      accessor: "name",
                    },
                    {
                      Header: t("Count"),
                      accessor: "count",
                    },
                  ]}
                  rowsData={rules?.products}
                />
              )}
            </Card>
          );
        })}

      {/* <Card className="delivery-preview-card">
        {item?.conditions?.length > 0 && (
          <DetailsTable
            className="campaign-attribute-table"
            cardHeader={{ value: "Condition" }}
            columnsData={[
              {
                Header: t("Attribute"),
                accessor: "attribute",
              },
              {
                Header: t("Operator"),
                accessor: "operator",
              },
              {
                Header: t("Value"),
                accessor: "value",
              },
            ]}
            rowsData={item?.conditions}
          />
        )}
        {item?.products?.length > 0 && (
          <DetailsTable
            className="campaign-product-table"
            // cardHeader={{ value: "Product Details" }}
            columnsData={[
              {
                Header: t("Product"),
                accessor: "name",
              },
              {
                Header: t("Count"),
                accessor: "count",
              },
            ]}
            rowsData={item?.products}
          />
        )}
      </Card> */}
    </>
  );
};

export default CycleDataPreview;
