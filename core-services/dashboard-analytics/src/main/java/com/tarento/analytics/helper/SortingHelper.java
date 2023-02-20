package com.tarento.analytics.helper;
import org.springframework.stereotype.Component;
import com.tarento.analytics.dto.Data;
import com.tarento.analytics.dto.Plot;
import java.util.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SortingHelper {
    public List<Data> sort(String sortingKey, List<Data> dataList) {
        Comparator<Plot> PlotSortComparator = new Comparator<Plot>() {
            public int compare(Plot p1, Plot p2) {
                String Plotname1
                        = p1.getName().toUpperCase();
                String Plotname2
                        = p2.getName().toUpperCase();
                Double Plotvalue1
                        = p1.getValue();
                Double Plotvalue2
                        = p2.getValue();

                if ((sortingKey).equals("sortKeyAsc")) {
                    return Plotname1.compareTo(
                            Plotname2);
                } else if ((sortingKey).equals("sortKeyDesc")) {
                    return Plotname2.compareTo(
                            Plotname1);
                } else if ((sortingKey).equals("sortValueAsc") && dataList.size() == 1) {
                    if (Plotvalue1 < Plotvalue2) return -1;
                    if (Plotvalue1 > Plotvalue2) return 1;
                    return 0;
                } else if ((sortingKey).equals("sortValueDesc") && dataList.size() == 1) {
                    if (Plotvalue1 < Plotvalue2) return 1;
                    if (Plotvalue1 > Plotvalue2) return -1;
                    return 0;
                }
                return 0;
            }
        };
        for (int i=0; i<dataList.size(); i++) {
            List<Plot> plotlist = new ArrayList<Plot>(dataList.get(i).getPlots());
            plotlist.sort(PlotSortComparator);
            dataList.get(i).setPlots(plotlist);
        }
        return dataList;
    }

}