package com.girlkun.models.boss.list_boss.TrainOffline;

import com.girlkun.models.boss.*;
import com.girlkun.models.map.Zone;

/**
 * @Stole By HUU QUYEN ZALO 0932399207
 */
public class Bill extends TrainBoss {

    public Bill(byte bossID, BossData bossData, Zone zone, int x, int y) throws Exception {
        super(BossID.BILL, BossesData.BILL, zone, x, y);
    }
}
