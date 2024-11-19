package com.tarento.analytics.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tarento.analytics.enums.ChartType;

/**
 * @author Darshan Nagesh
 *
 */
public class AggregateDto {

	private ChartType chartType;

	private String visualizationCode;

	private String predictionPath;

	private Boolean showFooter;
	private String chartFormat;

	private Boolean showLabel;

	private Boolean stackBars;

	private String drillDownChartId;

	private Boolean hideInsights;

	private Boolean hideHeaderDenomination;
	private String plotLabel;

	public String getPlotLabel() {
		return plotLabel;
	}

	public void setPlotLabel(String plotLabel) {
		this.plotLabel = plotLabel;
	}

	private String targetLineChart;

	public Boolean getHideInsights() {
		return hideInsights;
	}

	public void setHideInsights(Boolean hideInsights) {
		this.hideInsights = hideInsights;
	}

	public String getPredictionPath(){ return predictionPath;}

	public void setPredictionPath(String predictionPath){ this.predictionPath = predictionPath;}

	public Boolean getShowFooter(){return showFooter;}
	public void setShowFooter(Boolean showFooter){this.showFooter = showFooter;}

	public Boolean getHideHeaderDenomination() {
		return hideHeaderDenomination;
	}

	public void setHideHeaderDenomination(Boolean hideHeaderDenomination) {
		this.hideHeaderDenomination = hideHeaderDenomination;
	}

	public Boolean getShowLabel() {
		return showLabel;
	}

	public void setShowLabel(Boolean showLabel) {
		this.showLabel = showLabel;
	}

	public String getVisualizationCode() {
		return visualizationCode;
	}

	public void setVisualizationCode(String visualizationCode) {
		this.visualizationCode = visualizationCode;
	}

	public String getDrillDownChartId() {
		return drillDownChartId;
	}

	public void setDrillDownChartId(String drillDownChartId) {
		this.drillDownChartId = drillDownChartId;
	}

	private Map<String, Object> customData;

	private RequestDate dates;

	private Object filter;

	private List<Data> data = new ArrayList<>();

	public List<Data> getData() {
		return data;
	}

	public void setData(List<Data> data) {
		this.data = data;
	}

	public ChartType getChartType() {
		return chartType;
	}

	public void setChartType(ChartType chartType) {
		this.chartType = chartType;
	}

	public String getChartFormat() {
		return chartFormat;
	}

	public void setChartFormat(String chartFormat) {
		this.chartFormat = chartFormat;
	}


	public Map<String, Object> getCustomData() {
		return customData;
	}

	public void setCustomData(Map<String, Object> customData) {
		this.customData = customData;
	}

	public RequestDate getDates() {
		return dates;
	}

	public void setDates(RequestDate dates) {
		this.dates = dates;
	}

	public Object getFilter() {
		return filter;
	}

	public void setFilter(Object filter) {
		this.filter = filter;
	}

	public String getTargetLineChart() {
		return targetLineChart;
	}

	public void setTargetLineChart(String targetLineChart) {
		this.targetLineChart = targetLineChart;
	}

    public Boolean getStackBars() {
        return stackBars;
    }

    public void setStackBars(Boolean stackBars) {
        this.stackBars = stackBars;
    }
}
