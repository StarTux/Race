package com.cavetale.race.sql;

import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow.UniqueKey;
import java.util.Date;
import java.util.UUID;
import lombok.Data;

@Data
@NotNull
@UniqueKey({"path", "player"})
@Name("player_records")
public final class SQLPlayerMapRecord implements SQLRow {
    @Id private Integer id;
    private String path;
    private UUID player;
    private long time;
    private Date date;
    private transient int rank;

    public SQLPlayerMapRecord() { }

    public SQLPlayerMapRecord(final String path, final UUID player, final long time) {
        this.path = path;
        this.player = player;
        this.time = time;
        this.date = new Date();
    }

    public void setNow() {
        date = new Date();
    }
}
