package com.cavetale.race.struct;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public final class Areas {
    protected List<Area> winner = new ArrayList<>();
    protected List<Area> viewer = new ArrayList<>();
}
