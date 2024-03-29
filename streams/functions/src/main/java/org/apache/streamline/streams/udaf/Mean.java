package org.apache.streamline.streams.udaf;


import org.apache.streamline.streams.rule.UDAF;

public class Mean implements UDAF<StddevOnline, Number, Double> {
    @Override
    public StddevOnline init() {
        return new StddevOnline();
    }

    @Override
    public StddevOnline add(StddevOnline aggregate, Number val) {
        return aggregate.add(val);
    }

    @Override
    public Double result(StddevOnline aggregate) {
        return aggregate.mean();
    }
}
