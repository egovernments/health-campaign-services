import { Card, LabelFieldPair, Row } from "@egovernments/digit-ui-react-components";
import React, { Fragment } from "react";
import { useTranslation } from "react-i18next";
import DetailsTable from "./DetailsTable";

const CycleDetaisPreview = ({ data, item, index }) => {
  const { t } = useTranslation();
  // const { cycleNumber, deliveryNumber } = data?.campaignDetails?.deliveryRules.reduce(
  //   (acc, { cycleNumber, deliveryNumber }) => ({
  //     cycleNumber: Math.max(acc.cycleNumber, cycleNumber),
  //     deliveryNumber: Math.max(acc.deliveryNumber, deliveryNumber),
  //   }),
  //   { cycleNumber: 0, deliveryNumber: 0 }
  // );
  return (
    <>
      <Row
        key={item?.cycleNumber}
        label={`${t("CYCLE_NUMBER")}`}
        text={item?.cycleNumber}
        className="border-none"
        rowContainerStyle={{ display: "flex" }}
        labelStyle={{ fontWeight: "700" }}
        textStyle={{ width: "60%" }}
      />
      <Row
        key={item?.deliveryNumber}
        label={`${t("DELIVERY_NUMBER")}`}
        text={item?.deliveryNumber}
        className="border-none"
        rowContainerStyle={{ display: "flex" }}
        labelStyle={{ fontWeight: "700" }}
        textStyle={{ width: "60%" }}
      />
      {/* <LabelFieldPair>
        <span>{`${t("CYCLE_NUMBER")}`}</span>
        <span>{item?.cycleNumber}</span>
      </LabelFieldPair>
      <LabelFieldPair>
        <span>{`${t("DELIVERY_NUMBER")}`}</span>
        <span>{item?.deliveryNumber}</span>
      </LabelFieldPair> */}
      {item?.startDate || item?.endDate ? (
        <Card className="card-with-background" style={{ maxWidth: "45%", marginLeft: "0px" }}>
          <div className="card-head">
            <h2>
              {t(`CYCLE`)} {item?.cycleNumber}
            </h2>
          </div>
          {item?.startDate && (
            <Row
              key={t(item?.startDate)}
              label={`${t("Start Date")}`}
              text={Digit.Utils.date.convertEpochToDate(item?.startDate)}
              className="border-none"
              rowContainerStyle={{ display: "flex" }}
              labelStyle={{ fontWeight: "700" }}
            />
          )}
          {item?.endDate && (
            <Row
              key={t(item?.endDate)}
              label={`${t("End Date")}`}
              text={Digit.Utils.date.convertEpochToDate(item?.endDate)}
              className="border-none"
              rowContainerStyle={{ display: "flex" }}
              labelStyle={{ fontWeight: "700" }}
            />
          )}
        </Card>
      ) : null}

      <Card className="delivery-preview-card">
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
        <DetailsTable
          className="campaign-product-table"
          // cardHeader={{ value: "Product Details" }}
          columnsData={[
            {
              Header: t("Product"),
              accessor: "product",
            },
            {
              Header: t("Count"),
              accessor: "count",
            },
          ]}
          rowsData={item?.products}
        />
      </Card>
    </>
  );
};

export default CycleDetaisPreview;
