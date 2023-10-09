package com.tarento.analytics.helper;
import org.springframework.stereotype.Component;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.tarento.analytics.handler.IResponseHandler.*;

@Component
public class SortingHelper {
    public List<Data> sort(String sortingKey, List<Data> dataList) {
        Boolean isValueSortingApplicable = dataList.size() == 1;
        Comparator<Plot> plotSortComparator = plotSortComparator(sortingKey, isValueSortingApplicable);
        for (int i=0; i<dataList.size(); i++) {
            List<Plot> plotList = new ArrayList<Plot>(dataList.get(i).getPlots());
            plotList.sort(plotSortComparator);
            dataList.get(i).setPlots(plotList);
        }
        return dataList;
    }
    public List<Data> tableSort(List<Data> dataList, String sortingKey) {
        Comparator<Data> tableSortComparator = tableSortComparator(sortingKey);
        dataList.sort(tableSortComparator);
        return dataList;
    }

    private static Comparator<Plot> plotSortComparator(String sortingKey, Boolean isValueSortingApplicable) {
        return new Comparator<Plot>() {
            public int compare(Plot p1, Plot p2) {
                String plotName1 = p1.getName().toUpperCase();
                String plotName2 = p2.getName().toUpperCase();
                Double plotValue1 = p1.getValue();
                Double plotValue2 = p2.getValue();

                if (sortingKey.equals(SORT_KEY_ASC)) {
                    return plotName1.compareTo(plotName2);
                } else if (sortingKey.equals(SORT_KEY_DESC)) {
                    return plotName2.compareTo(plotName1);
                } else if (sortingKey.equals(SORT_VALUE_ASC) && isValueSortingApplicable) {
                    if (plotValue1 < plotValue2) return -1;
                    if (plotValue1 > plotValue2) return 1;
                    return 0;
                } else if (sortingKey.equals(SORT_VALUE_DESC) && isValueSortingApplicable) {
                    if (plotValue1 < plotValue2) return 1;
                    if (plotValue1 > plotValue2) return -1;
                    return 0;
                }
                return 0;
            }
        };
    }

    private static Comparator<Data> tableSortComparator(String sortingKey) {
        return (p1, p2) -> {
            String plotName1 = p1.getHeaderName().toUpperCase();
            String plotName2 = p2.getHeaderName().toUpperCase();

            if (sortingKey.equals(SORT_KEY_ASC)) {
                return plotName1.compareTo(plotName2);
            } else if (sortingKey.equals(SORT_KEY_DESC)) {
                return plotName2.compareTo(plotName1);
            }
            return 0;
        };
    }

}