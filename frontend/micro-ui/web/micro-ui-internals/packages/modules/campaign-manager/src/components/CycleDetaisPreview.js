import { Card, LabelFieldPair, Row } from "@egovernments/digit-ui-react-components";
import React, { Fragment } from "react";
import { useTranslation } from "react-i18next";
import DetailsTable from "./DetailsTable";

function mergeObjects(item) {
  const arr = item?.conditions;
  const mergedArr = [];
  const mergedAttributes = new Set();

  arr.forEach((obj) => {
    if (!mergedAttributes.has(obj.attribute)) {
      const sameAttrObjs = arr.filter((o) => o.attribute === obj.attribute);

      if (sameAttrObjs.length > 1) {
        const fromValue = Math.min(...sameAttrObjs.map((o) => o.value));
        const toValue = Math.max(...sameAttrObjs.map((o) => o.value));

        mergedArr.push({
          fromValue,
          toValue,
          value: fromValue > 0 && toValue > 0 ? `${fromValue} to ${toValue}` : null,
          operator: "IN_BETWEEN",
          attribute: obj.attribute,
        });

        mergedAttributes.add(obj.attribute);
      } else {
        mergedArr.push(obj);
      }
    }
  });

  return { ...item, conditions: mergedArr };
}

const CycleDetaisPreview = ({ data, items, index }) => {
  const { t } = useTranslation();
  const item = mergeObjects(items);

  return (
    <>
      <Row
        key={item?.cycleNumber}
        label={`${t("CYCLE_NUMBER")}`}
        text={item?.cycleNumber}
        className="border-none"
        rowContainerStyle={{ display: "flex", marginBottom: "1rem" }}
        labelStyle={{ fontWeight: "700" }}
        textStyle={{ width: "60%" }}
      />
      <Row
        key={item?.deliveryNumber}
        label={`${t("DELIVERY_NUMBER")}`}
        text={item?.deliveryNumber}
        className="border-none"
        rowContainerStyle={{ display: "flex", marginBottom: "1rem" }}
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
              labelStyle={{ fontWeight: "500" }}
            />
          )}
          {item?.endDate && (
            <Row
              key={t(item?.endDate)}
              label={`${t("End Date")}`}
              text={Digit.Utils.date.convertEpochToDate(item?.endDate)}
              className="border-none"
              rowContainerStyle={{ display: "flex" }}
              labelStyle={{ fontWeight: "500" }}
            />
          )}
        </Card>
      ) : null}

      <Card className="delivery-preview-card">
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
      </Card>
    </>
  );
};

export default CycleDetaisPreview;
