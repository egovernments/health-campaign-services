package org.egov.common.ds;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Tuple<X, Y> {
    X x;
    Y y;
}
